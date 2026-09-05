

refactor src/main/java/fr/an/jira/repository/JiraIssueRepository.java,
partition written files (currently thousands of small files), into sub dirs "/created_year={yyyy}/*"

also, compact all (snapshot) issues files into a single "/created_year={yyyy}/data.ndjson.zip", and add incremental insert/update/delete in "/created_year={yyyy}/changes.ndjson",
using nd-json files, wrapped by "{change='insert',data=...}", "{change='update', data=...}", "{change='delete', key=...}"  

write a small migration code to read small files and write compacted file per partitioned /created_year={yyyy}  