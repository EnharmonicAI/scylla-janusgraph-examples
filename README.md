# Scylla & JanusGraph Deployment Examples

Short code examples to pair with the article [Powering a Graph Data System with Scylla + JanusGraph](https://www.scylladb.com/2019/05/14/powering-a-graph-data-system-with-scylla-janusgraph/).  See the article for the full walkthrough!

The 3 components of our graph data system are:
* _Scylla_ - our storage backend, the ultimate place where our data gets stored
* _Elasticsearch_ - our index backend, speeding up some searches, and delivering powerful range and fuzzy-match capabilities
* _JanusGraph_ - provides our graph itself, either as a server or embedded in a standalone application

#### Scylla
Use Scylla's [GCE deployment script](https://github.com/scylladb/scylla-code-samples/tree/master/gce_deploy_and_install_scylla_cluster) to deploy Scylla on a 3 node cluster.
```
scylla-code-samples/gce_deploy_and_install_scylla_cluster/gce_deploy_and_install_scylla_cluster.sh \
  -p symphony-graph17038 \
  -z us-west1-b \
  -t n1-standard-16 \
  -n -c2 \
  -v3.0
```

Everything else can be handled from the code in this repository.

#### GKE Cluster
```
scripts/setup/setup_gke.sh -p MY-PROJECT
```

#### Deploy Elasticsearch
```
kubectl apply -f k8s/elasticsearch/es-storage.yaml
kubectl apply -f k8s/elasticsearch/es-service.yaml
kubectl apply -f k8s/elasticsearch/es-statefulset.yaml
```

#### Build and Deploy a JanusGraph image to Google Container Registry
You'll need this for your JanusGraph Gremlin Console & JanusGraph Server pods.
```
scripts/setup/build_and_deploy_janusgraph_image.sh -p MY-PROJECT
```

#### Launch Gremlin Console
This uses environment variables in the YAML file to help setup a JanusGraph configuration file.
Make sure you update the GCP Project and Scylla Hostname in __k8s/gremlin-console/janusgraph-gremlin-console.yaml__.
```
kubectl create -f k8s/gremlin-console/janusgraph-gremlin-console.yaml
kubectl exec -it janusgraph-gremlin-console -- bin/gremlin.sh

         \,,,/
         (o o)
-----oOOo-(3)-oOOo-----
...
gremlin> graph = JanusGraphFactory.open('/etc/opt/janusgraph/janusgraph.properties')
...
// And you're off!
```

#### Launch Gremlin Server
This uses environment variables in the YAML file to help setup a JanusGraph configuration file.
Make sure you update the GCP Project and Scylla Hostname in __k8s/janusgraph/janusgraph-server.yaml__.
```
kubectl apply -f k8s/janusgraph/janusgraph-server-service.yaml
kubectl apply -f k8s/janusgraph/janusgraph-server.yaml
```

You can now connect to JanusGraph Server over the __janusgraph-service-lb__ load balancer IP.
