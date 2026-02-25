# Idra API List by Route

Source: `Idra/swagger.yaml` + backend methods in `AdministrationApi` and `ClientApi`.

## /administration APIs

### `/api/v1/administration/version`
- Description: Platform version
- **GET**: Returns platform version and release timestamp for the running Idra instance.

### `/api/v1/administration/dcat-ap/dump/{nodeID}`
- Description: Single Catalogues Dump
- **GET**: Returns the generated DCAT-AP dump for a single catalogue in RDF/XML or JSON, optionally forcing regeneration.

### `/api/v1/administration/dcat-ap/dump/download/{nodeID}`
- Description: Single Catalogues Dump Download
- **GET**: Downloads the DCAT-AP dump file for a single catalogue; supports force regeneration and ZIP output.

### `/api/v1/administration/dcat-ap/dump`
- Description: All Catalogues Dump
- **GET**: Returns the global federation DCAT-AP dump in RDF/XML or JSON, optionally forcing regeneration.

### `/api/v1/administration/dcat-ap/dump/download`
- Description: Download All Catalogues Dump
- **GET**: Downloads the global federation DCAT-AP dump as file; supports force regeneration and ZIP output.

### `/api/v1/administration/logs`
- Description: Logs
- **POST**: Returns application logs filtered by level and date range.

### `/api/v1/administration/statistics/keyword`
- Description: Keyword Statistics
- **POST**: Returns aggregated keyword usage statistics for searches.

### `/api/v1/administration/statistics/search`
- Description: Search Statistics
- **POST**: Returns aggregated search statistics by country and time aggregation window.

### `/api/v1/administration/statistics/catalogues`
- Description: Catalogue Statistics
- **POST**: Returns aggregated catalogue statistics for selected nodes and date interval.

### `/api/v1/administration/cataloguesStatMinDate`
- Description: Get oldest statistic date
- **GET**: Returns the oldest available date for catalogue statistics.

### `/api/v1/administration/countries`
- Description: Get Countries
- **GET**: Returns countries available in statistics, with minimum available search-statistics date.

### `/api/v1/administration/verifyToken`
- Description: Token Verification
- **GET**: Validates the current bearer token and returns 200 if it is valid.

### `/api/v1/administration/logout`
- Description: Logout
- **POST**: Performs logout using the active authentication provider and invalidates current session context.

### `/api/v1/administration/login`
- Description: Login
- **POST**: Completes Keycloak code exchange and creates authentication cookies (access, refresh, username).

### `/api/v1/administration/prefixes/{prefixId}`
- Description: Single Prefix Management
- **GET**: Returns one RDF prefix by its identifier.
- **DELETE**: Deletes one RDF prefix by its identifier.
- **PUT**: Updates one RDF prefix by its identifier.

### `/api/v1/administration/prefixes`
- Description: RDF's Prefixes Management
- **POST**: Creates a new RDF prefix entry.
- **GET**: Returns all configured RDF prefixes.

### `/api/v1/administration/configuration`
- Description: Configuration Management
- **GET**: Returns current Idra platform configuration settings.
- **POST**: Updates Idra platform configuration settings.

### `/api/v1/administration/catalogues/{nodeId}/messages/{messageId}`
- Description: Catalogue Single Message
- **GET**: Returns one synchronization/runtime message for the selected catalogue.
- **DELETE**: Deletes one synchronization/runtime message for the selected catalogue.

### `/api/v1/administration/catalogues/{nodeId}/messages`
- Description: Catalogue Messages
- **GET**: Returns all synchronization/runtime messages for the selected catalogue.
- **DELETE**: Deletes all synchronization/runtime messages for the selected catalogue.

### `/api/v1/administration/catalogues/{nodeId}/synchronize`
- Description: Synchronize Catalogue
- **POST**: Forces immediate synchronization for the selected federated catalogue.

### `/api/v1/administration/catalogues/{nodeId}/deactivate`
- Description: Deactivate Catalogue
- **PUT**: Deactivates a catalogue; optional keepDatasets query parameter controls cached datasets retention.

### `/api/v1/administration/catalogues/{nodeId}/activate`
- Description: Activate Catalogue
- **PUT**: Activates a catalogue and restores dump content from file when required before activation.

### `/api/v1/administration/catalogues/{nodeId}`
- Description: Single Catalogue APIs
- **GET**: Returns full details for one federated catalogue, optionally including image data.
- **PUT**: Updates one federated catalogue (without changing node type or active state); can reschedule synchronization.
- **DELETE**: Unregisters one catalogue from federation and performs related cleanup (including CB integration when configured).

### `/api/v1/administration/catalogues`
- Description: Catalogues Resources
- **GET**: Returns federated catalogues for administration, including message counters; optional withImage flag.
- **POST**: Registers a new catalogue in federation (multipart), including dump payload handling for DCAT dump/ORION/SPARQL types.

### `/api/v1/administration/datalets`
- Description: Get All Datalets
- **GET**: Returns all persisted datalets, sorted by identifier.

### `/api/v1/administration/catalogues/{nodeID}/dataset/{datasetID}/distribution/{distributionID}/deleteDatalet/{dataletID}`
- Description: Delete Datalet
- **DELETE**: Deletes one datalet from a distribution and updates dataset metadata when no datalets remain.

## /client APIs

### `/api/v1/client/catalogues`
- Description: End User Catalogues Resources
- **GET**: Returns active catalogues for end users with filtering, ordering, and pagination parameters.

### `/api/v1/client/catalogues/{nodeId}`
- Description: Single Catalogue APIs
- **GET**: Returns one active catalogue for end users by id.

### `/api/v1/client/cataloguesInfo`
- Description: Catalogues Info
- **GET**: Returns lightweight catalogue info (id, name, federation level) for active and searchable catalogues.

### `/api/v1/client/search`
- Description: Metadata Search
- **POST**: Performs federated dataset search (live or cache) with filters, facets, sorting, EuroVoc options, and pagination.

### `/api/v1/client/search/dcat-ap`
- Description: Metadata Search DCATAP
- **POST**: Performs federated dataset search and serializes results as DCAT-AP in requested format/profile.

### `/api/v1/client/sparql/query`
- Description: Execute SPARQL Query
- **POST**: Executes federated SPARQL query and returns result payload plus declared content type.

### `/api/v1/client/dataset/{id}`
- Description: Dataset Detail
- **GET**: Returns one dataset by its global identifier from cache index.

### `/api/v1/client/catalogues/{nodeID}/dataset/{datasetID}/distribution/{distributionID}/createDatalet`
- Description: Create Datalet
- **POST**: Creates a datalet for a distribution, initializes metadata (views/dates), and marks distribution as having datalets.

### `/api/v1/client/catalogues/{nodeID}/dataset/{datasetID}/distribution/{distributionID}/datalets`
- Description: Get Datalets from Distribution
- **GET**: Returns all datalets associated with a specific node/dataset/distribution triple.

### `/api/v1/client/catalogues/{nodeID}/dataset/{datasetID}/distribution/{distributionID}/datalet/{dataletID}/updateViews`
- Description: Update Datalet Views
- **PUT**: Increments datalet views counter and updates last-seen timestamp.

## /odf APIs

### `/api/v1/odf/odms/search`
- Description: Datasets Search
- **POST**: ODMS-side federation API: executes native catalogue search using received filters and returns matching datasets.

### `/api/v1/odf/odms/datasets`
- Description: Retrieve Paginated Datasets
- **GET**: ODMS-side federation API: returns paginated list of native datasets.

### `/api/v1/odf/odms/datasets/{datasetID}`
- Description: Retrieve Single Dataset
- **GET**: ODMS-side federation API: returns one native dataset by identifier.

### `/api/v1/odf/odms/datasets/info`
- Description: Datasets Summary
- **GET**: ODMS-side federation API: returns lightweight datasets summary/identifiers with issued/modified timestamps.

