# openehr-rest-client

## run just one test

`$ gradle test --tests "OpenEhrRestClientTest.create demographic family trees"`

## run tests with dots in the names (using wild cards)

`$ gradle test --tests "OpenEhrRestClientTest.B*4*a* get composition at version"`

## run all tests inside a category

`$ gradle test --tests "OpenEhrRestClientTest.B*4*"`