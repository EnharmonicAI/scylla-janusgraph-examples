// Define Schema and setup
//
// Run from a Gremlin Console after defining the JanusGraphConfig filepath
// (In our test deployment, it is '/etc/opt/janusgraph/janusgraph.properties')
//
// We can instantiate our graph with:
// janusGraphConfig = '/etc/opt/janusgraph/janusgraph.properties'
// graph = JanusGraphFactory.open(janusGraphConfig)

//-----------------------
// Load the Initial Schema
//-----------------------
mgmt = graph.openManagement()

// Define Vertex labels
Candidate = mgmt.makeVertexLabel("Candidate").make()
Contribution = mgmt.makeVertexLabel("Contribution").make()
IndividualContributor = mgmt.makeVertexLabel("IndividualContributor").make()
OrganizationContributor = mgmt.makeVertexLabel("OrganizationContributor").make()
State = mgmt.makeVertexLabel("State").make()
Zipcode = mgmt.makeVertexLabel("Zipcode").make()

// Define Edge labels - the relationships between Vertices
CONTRIBUTION_TO = mgmt.makeEdgeLabel("CONTRIBUTION_TO").multiplicity(MANY2ONE).make()
CONTRIBUTED = mgmt.makeEdgeLabel("CONTRIBUTED").multiplicity(SIMPLE).make()
STATE = mgmt.makeEdgeLabel("STATE").multiplicity(SIMPLE).make()
ZIP = mgmt.makeEdgeLabel("ZIP").multiplicity(SIMPLE).make()

// Define Vertex Property Keys

// Util Properties
type = mgmt.makePropertyKey("type").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()


// Candidate
name = mgmt.makePropertyKey("name").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
filerCommitteeIdNumber = mgmt.makePropertyKey("filerCommitteeIdNumber").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()

mgmt.addProperties(Candidate, type, name, filerCommitteeIdNumber)


// Contribution
transactionId = mgmt.makePropertyKey("transactionId").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
// TODO: Make Date
contributionDate = mgmt.makePropertyKey("contributionDate").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
amount = mgmt.makePropertyKey("amount").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
formType = mgmt.makePropertyKey("formType").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
contributionType = mgmt.makePropertyKey("contributionType").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
// TODO: Make Boolean
itemized = mgmt.makePropertyKey("itemized").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
firstTimeDonor = mgmt.makePropertyKey("firstTimeDonor").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
firstTimeItemized = mgmt.makePropertyKey("firstTimeItemized").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()

mgmt.addProperties(Contribution, type, transactionId, contributionDate,
                  amount, formType, contributionType, itemized,
                  firstTimeDonor, firstTimeItemized)


// IndividualContributor
firstName = mgmt.makePropertyKey("firstName").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
lastName = mgmt.makePropertyKey("lastName").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
middleName = mgmt.makePropertyKey("middleName").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
prefix = mgmt.makePropertyKey("prefix").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
suffix = mgmt.makePropertyKey("suffix").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()

mgmt.addProperties(IndividualContributor, type, firstName, lastName,
                   middleName, prefix, suffix)

// OrganizationContributor
organizationName = mgmt.makePropertyKey("organizationName").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()

mgmt.addProperties(OrganizationContributor, type, organizationName)


// State
postalAbbreviation = mgmt.makePropertyKey("postalAbbreviation").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()

mgmt.addProperties(State, type, postalAbbreviation)


// Zipcode
fiveDigit = mgmt.makePropertyKey("fiveDigit").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()
rawZip = mgmt.makePropertyKey("rawZip").
    dataType(String.class).cardinality(Cardinality.SINGLE).make()

mgmt.addProperties(Zipcode, type, fiveDigit, rawZip)


// Define connections as (EdgeLabel, VertexLabel out, VertexLabel in)
mgmt.addConnection(CONTRIBUTION_TO, Contribution, Candidate)
mgmt.addConnection(CONTRIBUTED, IndividualContributor, Contribution)
mgmt.addConnection(CONTRIBUTED, OrganizationContributor, Contribution)
mgmt.addConnection(STATE, OrganizationContributor, State)
mgmt.addConnection(STATE, IndividualContributor, State)
mgmt.addConnection(ZIP, OrganizationContributor, Zipcode)
mgmt.addConnection(ZIP, IndividualContributor, Zipcode)

mgmt.commit()

// Add basic indices
mgmt = graph.openManagement()
mgmt.buildIndex("byType", Vertex.class).addKey(mgmt.getPropertyKey("type")).buildCompositeIndex()
mgmt.buildIndex("byCandidateIdNumber", Vertex.class).addKey(mgmt.getPropertyKey("filerCommitteeIdNumber")).
  unique().indexOnly(mgmt.getVertexLabel("Candidate")).buildCompositeIndex()
mgmt.buildIndex("byContributionTransactionId", Vertex.class).addKey(mgmt.getPropertyKey("transactionId")).
  unique().indexOnly(mgmt.getVertexLabel("Contribution")).buildCompositeIndex()
mgmt.buildIndex("byOrganizationContributorName", Vertex.class).addKey(mgmt.getPropertyKey("organizationName")).
  unique().indexOnly(mgmt.getVertexLabel("OrganizationContributor")).buildCompositeIndex()
mgmt.buildIndex("byIndividualContributorName", Vertex.class).
  addKey(mgmt.getPropertyKey("firstName")).addKey(mgmt.getPropertyKey("lastName")).
  unique().indexOnly(mgmt.getVertexLabel("IndividualContributor")).buildCompositeIndex()
mgmt.buildIndex("byStateAbbreviation", Vertex.class).addKey(mgmt.getPropertyKey("postalAbbreviation")).
  unique().indexOnly(mgmt.getVertexLabel("State")).buildCompositeIndex()
mgmt.buildIndex("byZipcodeFiveDigit", Vertex.class).addKey(mgmt.getPropertyKey("fiveDigit")).
  unique().indexOnly(mgmt.getVertexLabel("Zipcode")).buildCompositeIndex()

mgmt.commit()


// Ensure all indices are enabled
graph.getOpenTransactions().forEach { tx -> tx.rollback() }
mgmt = graph.openManagement()
mgmt.getGraphIndexes(Vertex.class).forEach { idx ->
  if (idx.getIndexStatus(idx.fieldKeys[0]) == SchemaStatus.INSTALLED) {
    mgmt.updateIndex(idx, SchemaAction.REGISTER_INDEX).get()
  }
}
mgmt.commit()
sleep(20000)
mgmt = graph.openManagement()
mgmt.getGraphIndexes(Vertex.class).forEach { idx ->
  if (idx.getIndexStatus(idx.fieldKeys[0]) == SchemaStatus.REGISTERED) {
    mgmt.updateIndex(idx, SchemaAction.ENABLE_INDEX).get()
  }
}
mgmt.commit()
