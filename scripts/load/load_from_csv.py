#!/usr/bin/env python
"""Load data from a csv file into JanusGraph."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import csv
from timeit import default_timer as timer

from absl import app
from absl import flags

from ruamel.yaml import YAML

from gremlin_python.process.anonymous_traversal import traversal
from gremlin_python.driver.driver_remote_connection import DriverRemoteConnection
from gremlin_python.process.graph_traversal import __
from gremlin_python.process.traversal import P

# ARGS FLAGS
flags.DEFINE_string(
    'data', None,
    'Path to the source data to be loaded into the graph.')
flags.DEFINE_string(
    'mapping', None,
    'Path to the mapping file used to determine the objects that '
    'will be loaded into the graph from the data file.')
flags.DEFINE_string(
    'hostname', None,
    'Gremlin Server hostname where we want to connect and load the data.')
flags.DEFINE_integer(
    'row_limit', None,
    'Limit the number of rows to try to insert from provided data file. '
    '(Useful for initial testing)')

FLAGS = flags.FLAGS

# DEFAULT_VARS
DEFAULT_HOST = 'localhost'
DEFAULT_PORT = 8182


def get_traversal_source(host=None, port=None):
    """Get a Traversal Source from a Gremlin Server connection."""
    if not host:
        host = DEFAULT_HOST
    if not port:
        port = DEFAULT_PORT
    connection_string = 'ws://{0}:{1}/gremlin'.format(host, port)

    g = traversal().withRemote(DriverRemoteConnection(connection_string, 'g'))

    return g


def get_element_counts(expected_elements, g):
    """Queries the graph for counts of entities loaded."""
    start_time = timer()
    unique_vertices = set([vertex['vertex_label'] for vertex in expected_elements['vertices']])
    for vertex_label in unique_vertices:
        print('{0} {1} vertices'.format(
                g.V().has('type', vertex_label).count().next(), vertex_label))

    unique_edges = set([edge['edge_label'] for edge in expected_elements['edges']])
    for edge_label in unique_edges:
        print('{0} {1} edges'.format(
                g.E().hasLabel(edge_label).count().next(), edge_label))

    end_time = timer()
    count_time = end_time - start_time

    print('Count time: {0:.1f} sec'.format(count_time))


def get_lookup_values(record, lookup_properties):
    """Gets lookup values from a lookup_properties dictionary and a record."""
    lookup_values = {}
    for source_field, prop_key in lookup_properties.items():
        lookup_value = record[source_field]
        if not lookup_value or len(lookup_value) < 1:
            return None
        lookup_values[prop_key] = lookup_value

    return lookup_values



def upsert_vertex(record, vertex_mapping, g):
    vertex_label = vertex_mapping['vertex_label']

    # Ensure all lookup values are present first
    lookup_values = get_lookup_values(record, vertex_mapping['lookup_properties'])
    if lookup_values is None:
        return

    # Setup traversals
    try:
        traversal = g.V().hasLabel(vertex_label)
        insertion_traversal = __.addV(vertex_label).property('type', vertex_label)

        for prop_key, lookup_value in lookup_values.items():
            traversal = traversal.has(prop_key, lookup_value)
            insertion_traversal = insertion_traversal.property(prop_key, lookup_value)

        # Add Vertex insertion partial traversal
        for source_field, prop_key in vertex_mapping['other_properties'].items():
            insertion_traversal = insertion_traversal.property(prop_key,
                                                               record[source_field])

        traversal.fold().coalesce(__.unfold(), insertion_traversal).next()
    except:
        print("Vertex error - skipping: {0}({1})".format(vertex_label, lookup_values))


def upsert_edge(record, edge_mapping, g):
    edge_label = edge_mapping['edge_label']
    # Simple logic, requiring that Vertices must exist before edge can be added.
    # Ensure all lookup values are present first
    out_lookup_values = get_lookup_values(record, edge_mapping['out_vertex']['lookup_properties'])
    in_lookup_values = get_lookup_values(record, edge_mapping['in_vertex']['lookup_properties'])
    if out_lookup_values is None or in_lookup_values is None:
        return

    try:
        traversal = g.V().hasLabel(edge_mapping['out_vertex']['vertex_label'])
        insertion_traversal = __.V().hasLabel(edge_mapping['out_vertex']['vertex_label'])

        for prop_key, lookup_value in out_lookup_values.items():
            traversal = traversal.has(prop_key, lookup_value)
            insertion_traversal = insertion_traversal.has(prop_key, lookup_value)

        traversal = traversal.as_('out').V().hasLabel(edge_mapping['in_vertex']['vertex_label'])
        insertion_traversal = insertion_traversal.as_('out2').V().hasLabel(edge_mapping['in_vertex']['vertex_label'])

        for prop_key, lookup_value in in_lookup_values.items():
            traversal = traversal.has(prop_key, lookup_value)
            insertion_traversal = insertion_traversal.has(prop_key, lookup_value)

        insertion_traversal = insertion_traversal.addE(edge_label).from_('out2')
        traversal = traversal.as_('in').inE(edge_label).as_('e').outV().where(P.eq('out')).fold().coalesce(
                __.unfold(), insertion_traversal).next()

    except:
        print("Edge error - skipping: {0}({1}) --{2}-> {3}({4})".format(
                edge_mapping['out_vertex']['vertex_label'],
                out_lookup_values,
                edge_label,
                edge_mapping['in_vertex']['vertex_label'],
                in_lookup_values))


def load_from_csv(filename, record_mapping, g):
    """Loads vertices and edges from a csv file, based on a record mapping."""
    print("Loading rows from CSV into Graph")
    start_time = timer()
    with open(filename, 'r') as f:
        reader = csv.DictReader(f)
        row_count = 0

        for row in reader:
            # Insert graph entities from record.
            for vertex_mapping in record_mapping['vertices']:
                upsert_vertex(row, vertex_mapping, g)

            # Count as we go?
            for edge_mapping in record_mapping['edges']:
                upsert_edge(row, edge_mapping, g)

            row_count += 1

            if row_count % 100 == 0:
                print("Loaded {0} rows".format(row_count))

            if FLAGS.row_limit and row_count >= FLAGS.row_limit:
                break

    end_time = timer()
    load_time = end_time - start_time

    print('Load time: {0:.1f} sec - {1} records'.format(load_time, row_count))
    print('({0:.1f} records / sec)'.format(row_count / load_time))


def get_record_mapping_from_yaml(filename):
    """Gets a record mapping dictionary from a YAML file."""
    with open(filename, 'r') as f:
        yaml = YAML(typ='safe')
        record_mapping = yaml.load(f)

    return record_mapping


def main(argv):
    if not FLAGS.data:
        print('Error: Data file path must be supplied (--data).')
        return
    if not FLAGS.mapping:
        print('Error: Mapping file path must be supplied (--mapping).')
        return
    if not FLAGS.hostname:
        print('Error: Gremlin Server hostname must be supplied (--hostname).')
        return

    g = get_traversal_source(FLAGS.hostname, 8182)

    record_mapping = get_record_mapping_from_yaml(FLAGS.mapping)
    load_from_csv(FLAGS.data, record_mapping, g)

    get_element_counts(record_mapping, g)


if __name__ == '__main__':
    app.run(main)
