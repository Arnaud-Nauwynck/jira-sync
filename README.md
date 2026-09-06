# Jira Synchronization to cached local files


Goal: synchronize on demand all new/updates Jira Issues of a project,
to be able to query issues fast, using statistics, ag-grid search/sort/filter, etc

# Performances

It is very fast to query, and then all browsing search are blazing fast!
![screenshot-devtools-initial-load.png](doc/screenshot-devtools-initial-load.png)

The first time, data parse local files, and they are then cached (both server-side and client side)
![screenshot-intellij-initial-load.png](doc/screenshot-intellij-initial-load.png)

ag-grid contains all issues ( ~55 000) in-memory, which only represents ~400 Mo of heap
![screenshot-devtools-memory-usage.png](doc/screenshot-devtools-memory-usage.png)

Reloading on client takes ~14s
![screenshot-devtools-reload.png](doc/screenshot-devtools-reload.png)


# Screenshots


![screenshot-issues-list.png](doc/screenshot-issues-list.png)

Master-Detail 
![screenshot-issues-list-master-details.png](doc/screenshot-issues-list-master-details.png)

Showing issue as page
![screenshot-issue-details.png](doc/screenshot-issue-details.png)


User statistics for creating Issues (sorted per total count, descending)
![screenshot-user-issues-create-statistics.png](doc/screenshot-user-issues-create-statistics.png)

filtering on username
![screenshot-user-issues-create-statistics-filter.png](doc/screenshot-user-issues-create-statistics-filter.png)