# New Relic NRQL Support

As of 2.5.1091, HoneySQL provides some support for New Relic's NRQL query language.

At present, the following additional SQL clauses (and their corresponding
helper functions) are supported:

* `:facet` - implemented just like `:select`
* `:since` - implemented like `:interval`
* `:until` - implemented like `:interval`
* `:compare-with` - implemented like `:interval`
* `:timeseries` - implemented like `:interval`

> Note: `:timeseries :auto` is the shortest way to specify a timeseries.

When you select the `:nrql` dialect, SQL formatting assumes `:inline true`
so that the generated SQL string can be used directly in NRQL queries.

In addition, stropping (quoting) is done using backticks, like MySQL,
but entities are not split at `/` or `.` characters, so that:

```
:foo/bar.baz ;;=> `foo/bar.baz`
```

```clojure
user=> (require '[honey.sql :as sql])
nil
```
```clojure
user=> (sql/format {:select [:mulog/timestamp :mulog/event-name]
                    :from   :Log
                    :where  [:= :mulog/data.account "foo-account-id"]
                    :since  [2 :days :ago]
                    :limit 2000}
                    {:dialect :nrql :pretty true})
["
SELECT `mulog/timestamp`, `mulog/event-name`
FROM Log
WHERE `mulog/data.account` = 'foo-account-id'
LIMIT 2000
SINCE 2 DAYS AGO
"]
```
