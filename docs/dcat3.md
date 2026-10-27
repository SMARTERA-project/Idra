# DCAT-AP 3.0.1 in Idra

Questo documento descrive come Idra acquisisce, conserva ed esporta i metadati DCAT-AP 3.0.1, cosa
cambia per chi installa una nuova versione e quali limiti restano. Sostituisce le note di lavoro dei
singoli piani (44–62).

## Percorso dei metadati

```
fonte (dump RDF, CKAN, Socrata, SPOD, OpenDataSoft, GeoNetwork, NGSI-LD)
  → connettore → bean DCAT (MySQL + Solr) → API /client, export RDF /client/dcat-ap/dump
                                         → bridge NGSI-LD → Orion-LD
```

- **Campi strutturati**: i bean `beans/dcat/*` (Dataset, Distribution, DataService, DatasetSeries,
  CatalogRecord, Agent, Location, PeriodOfTime, LicenseDocument, Standard, Checksum, Relationship…)
  sono persistiti in MySQL e indicizzati in Solr. Sono quelli usati da ricerca, filtri e portale.
- **Envelope dei metadati** (`dcat_dataset.metadataEnvelope`, JSON versionato): per le fonti RDF
  conserva le triple originali uscenti dal Dataset (`sourceRdf`), la proiezione tipizzata e
  l'evidenza dei mapping verso i vocabolari controllati. L'export ripristina da qui le proprietà che i
  campi strutturati non rappresentano; titolo, descrizione e identificatore modificati restano
  prevalenti. Il bridge trasporta l'envelope come Property NGSI-LD.
- **Metadati del catalogo sorgente** (`odms.sourceMetadata`): la descrizione del `dcat:Catalog` della
  fonte, senza i membri (dataset, servizi, record), con i sotto-cataloghi descritti come
  `dcat:Catalog`. Nell'export la fonte prevale proprietà per proprietà sulla configurazione Idra.
- **Record di catalogo**: `dct:source` del record (`dcat_catalogue_record.sourceRecord`) e profili
  collegati al record.
- **Risorse autonome**: dump con soli DataService o DatasetSeries sono acquisiti senza Dataset
  artificiali (`odms.standaloneRdf`).
- **Nomi degli agenti** con tag di lingua (`dcat_agent_name.language`; nel JSON `name` resta un
  elenco di stringhe e le lingue viaggiano in `nameLanguage`).

## Connettori non RDF

Ogni connettore converte in DCAT solo ciò che ha un equivalente chiaro nella fonte:

| Connettore | Conversioni principali |
|---|---|
| CKAN / DCAT-AP_IT | geometria `spatial`, `bbox-*`, sistema di riferimento (`conformsTo`), copertura temporale, `creator` multilingua, `responsible-party`, `themes_aggregate`, licenza della risorsa da `license_type` (IRI), dimensione solo se presente |
| Socrata | `accessLevel` → diritti di accesso EU, `describedBy` → schema della distribuzione |
| SPOD | titolo della licenza, dimensione solo se presente |
| OpenDataSoft | allegati come documentazione |
| GeoNetwork ISO 19139 | periodo GML 3.2, contatto del record, frequenza ISO → vocabolario EU, lineage |

CKAN, Socrata e SPOD non conservano un envelope: ciò che non ha una conversione non arriva nell'export.

## Nessun valore inventato

Quando la fonte non fornisce un valore, Idra lo lascia vuoto:

- `dct:issued` / `dct:modified`: nessuna data `1970-01-01T00:00:00Z`. I valori salvati da versioni
  precedenti vengono ripuliti alla lettura (`DcatProperty.withoutInventedDate`). Il portale nasconde
  già le date assenti.
- `dcat:landingPage`: mai l'IRI del Dataset al suo posto; un landing page letterale della fonte è
  conservato.
- `dct:accrualPeriodicity`: nessuna frequenza `UNKNOWN`; un testo che non è un codice EU è esportato
  come etichetta della frequenza.
- licenza: nessun nome `unknown`, nessun tipo ricavato dal codice della licenza, nessun IRI
  segnaposto `http://purl.org/dc/terms/license` (ripulito anche alla lettura).
- `dcat:byteSize`: nessuna dimensione 0 quando la risorsa CKAN/SPOD non la dichiara.
- tipi media: nessun IRI `media-types/<testo>` inesistente; i codici di formato usati come tipo
  media (OpenDataSoft: `rdfxml`, `geojson`, `turtle`…) diventano il tipo IANA corrispondente.
- frequenze scritte a parole (OpenDataSoft: "Annuelle", "Mensuelle"…) diventano il codice EU.
- formati: nessun IRI `file-type/...` inesistente. Il formato è il concetto EU corrispondente
  (`csv` → `CSV`, `.kmz` → `KMZ`, `GeoJSON` → `GEOJSON`, `WMS` → `WMS_SRVC`); un IRI della fonte è
  conservato; un testo senza equivalente è esportato come `dct:MediaTypeOrExtent` con etichetta.
- GeoNetwork: nessun diritto di accesso `otherRestrictions`, formato `UNKNOWN` o titolo `resource`
  quando il record non li contiene.

Restano derivati, non inventati: il titolo del dataset GeoNetwork uguale all'identificativo quando
manca, la homepage del catalogo dall'host in registrazione.

## Export RDF

- `dcat:byteSize` è `xsd:nonNegativeInteger`.
- Lingue, formati, frequenze e landing page sono tipizzati secondo il range DCAT-AP. Dopo il ripristino
  dell'envelope, l'export aggiunge la classe di range agli oggetti non tipizzati
  (`DcatApSerializer.addRangeClasses`); agenti e concetti sono esclusi perché la classe porterebbe
  nomi ed etichette obbligatori che la fonte non ha. I valori testuali di proprietà che lo standard
  vuole come nodo (formato, tipo media, frequenza, diritti, licenza, provenienza) diventano il
  concetto EU/IANA corrispondente o un nodo con quel testo come etichetta; le dimensioni scritte come
  testo diventano interi. La copia della fonte salvata da Idra non è modificata.

## Validazione

La validazione completa usa il profilo DCAT-AP 3.0.1 ufficiale *full* (`shapes.ttl`, `range.ttl`,
`mdr-vocabularies.shape.ttl`, con ontologie e vocabolari EU importati) e, separatamente, le
raccomandazioni (`shapes_recommended.ttl`). Una copia delle shape è in
`src/test/resources/dcat3/shacl/`.

Risultato del 27 ottobre 2026 (ambiente di prova, fonti congelate, profilo *full*; numero di violazioni):

| Fonte | Fonte originale | Export Idra prima | Export Idra ora |
|---|---|---|---|
| Catalogo di prova massimale (dump DCAT, 133 coppie classe/proprietà) | 0 | 5 introdotte da Idra | 0 (133/133 conservate) |
| Eurostat D01 (dump DCAT) | 98 | — | 2 |
| Palermo (dump DCAT-AP_IT, 1.684 dataset) | 11.959 | — | 7 |
| Genova (CKAN) | — | 2.951 | 0 |
| Umbria (CKAN) | — | 3.086 | 178 |
| Howard County (Socrata) | — | 1.174 | 0 |
| Pisa (SPOD) | — | 667 | 4 |
| Parigi (OpenDataSoft) | — | 40.406 | 3.097 |
| Torino (GeoNetwork ISO 19139) | — | 2.527 | 505 |

Le violazioni residue sono limiti dei dati della fonte e non vengono corrette inventando valori:
formati e tipi media senza equivalente nei vocabolari EU/IANA (es. "tif - tfw", `OGC:WMS`, che è un
protocollo e non un formato), frequenze senza equivalente ("Ponctuelle"), dataset senza descrizione,
indirizzi non validi (con spazi), agenti e concetti EuroVoc citati senza nome o etichetta. Il profilo italiano DCAT-AP_IT non è
validabile integralmente: il file SHACL ufficiale pubblicato è vuoto.

## Installazione di una nuova versione

1. Backup del database.
2. Migrazioni, nell'ordine, sullo schema Idra (tutte idempotenti):
   `docker/migrations/plan58-catalogue-display-names.sql`, `plan61-standalone-rdf.sql`,
   `plan62-record-profile-owner.sql`, `plan62-agent-homepage-text.sql`,
   `plan62-source-text-columns.sql`, `plan62-uri-text-columns.sql`,
   `plan50-catalogue-source-metadata.sql`, `plan50-record-source.sql`,
   `plan50-agent-name-language.sql`, `plan50-no-invented-dates.sql` (le precedenti `plan45-*`, `plan48-*` e
   `db-config/migrations/*` se non già applicate). Le nuove installazioni le hanno già in
   `db-config/idra_db.sql`.
3. Installare **insieme** Idra e il bridge NGSI-LD: condividono i bean DCAT.
4. Risincronizzare i cataloghi: envelope, metadati del catalogo sorgente, nomi multilingua e
   conversioni dei connettori si riempiono alla sincronizzazione successiva.

## Limiti noti

- Il bridge NGSI-LD espone senza autenticazione le API usate da Idra: va tenuto su rete interna.
- Cataloghi molto grandi (oltre circa 5.000 dataset) non sono stati provati su questa macchina.
- Nell'API e nel broker alcune proprietà opzionali restano solo nell'envelope (campi strutturati:
  API 90/133, broker 86/133 sul catalogo massimale; obbligatorie e raccomandate complete).
