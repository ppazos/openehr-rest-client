# openEHR REST Client

![](assets/3.jpeg)

This is the Groovy reference implementation of the [openEHR](https://www.openehr.org/) REST Client for connecting to openEHR REST API implementations.

It compiles to Java bytecode so it can be used as any Java library with any language that compiles to the JVM.

## Compile

`$ gradle build`

It generates a JAR file in the `build/libs` folder.


## Run tests

### run just one test

`$ gradle test --tests "OpenEhrRestClientTest.create demographic family trees"`

### run tests with dots in the names (using wild cards)

`$ gradle test --tests "OpenEhrRestClientTest.B*4*a* get composition at version"`

### run all tests inside a category

`$ gradle test --tests "OpenEhrRestClientTest.B*4*"`



## Use it


```groovy
// Setup authentication using a token
def token = "abc...."
def auth = new TokenAuth(token)

// Build the client
def client = new OpenEhrRestClient(
   "http://localhost:8090/openehr/v1",
   auth,
   ContentTypeEnum.JSON
)

// Set committer metadata headers
client.setCommitterHeader('name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')

// Upload EHR_STATUS template
String opt = this.getClass().getResource('/ehr_status_any_en_v1.opt').text
client.uploadTemplate(opt)

// Create EHR
client.createEhr()

// Commit a clinical document
// - Upload template for clinical document
// - Get sample document, parse it into a COMPOSITION, commit the COMPOSITION
String opt1 = this.getClass().getResource('/minimal_evaluation.opt').text
client.uploadTemplate(opt1)

String json_doc = this.getClass().getResource('/minimal_evaluation.en.v1_20230205.json').text
def parser = new OpenEhrJsonParserQuick()
def doc = parser.parseJson(json_doc)
client.createComposition(ehr.ehr_id.value, doc)

```