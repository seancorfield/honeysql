# Other Databases

There is a dedicated section for [PostgreSQL Support](postgres.md).
This section provides hints and tips for generating SQL for other
databases.

As a reminder, HoneySQL supports the following dialects out of the box:
* `:ansi` -- which is the default and provides broad support for PostgreSQL as well
* `:mysql` -- which includes MariaDB and Percona
* `:oracle`
* `:sqlserver` -- Microsoft SQL Server

For the most part, these dialects only change the "stropping" --
how SQL entities are quoted in the generated SQL -- but dialects
can change clause order and/or add dialect-specific clauses.

This section is a work-in-progress and more hints and tips will be
added over time for more databases.

## Precedence

The biggest difference between database dialects tends to be
precedence. MySQL actually has different precedence in the `SET`
clause but several databases disagree on the precedence of actual
"set" operations: `UNION`, `EXCEPT`, `INTERSECT`, etc.

HoneySQL tries to be fairly neutral in this area and follows ANSI SQL
precedence. This means that some databases may have problems with
complex SQL operations that combine multiple clauses with contentious
precedence. In general, you can solve this using the `:nest`
pseudo-clause in the DSL:

<!-- :test-doc-blocks/skip -->
```clojure
{:nest DSL}
;; will produce DSL wrapped in ( .. )
```

This should allow you to cater to various databases' precedence
peculiarities.

## BigQuery (Google)

Function names can be case-sensitive: you can use the "as-is" notation
for SQL entities to avoid conversion to upper-case: `[:'domain :ref]`
produces `domain(ref)` rather than `DOMAIN(ref)`.

## ClickHouse

This is another case-sensitive database than requires the "as-is"
notation described for **BigQuery** above.

`WITH expr AS ident` is supported as a core part of the DSL,
as of 2.4.962.

## MySQL

When you select the `:mysql` dialect, the precedence of `:set` is
changed. All the other databases get this correct.

`REPLACE INTO`, while specific to MySQL and SQLite, is supported as
a core part of the DSL, as `:replace-into`, as of 2.4.969.

## SQLite

Precedence of "set" operations: SQLite differs from other databases
in handling compound SQL operations that use multiple `UNION`,
`EXCEPT`, `INTERSECT` clauses. Use `:nest` to disambiguate your
intentions.
See issue [#462](https://github.com/seancorfield/honeysql/issues/462)
for some background on this.

`INSERT OR IGNORE INTO`: this syntax is specific to SQLite for
performing upserts. However, SQLite supports the PostgreSQL-style
upsert with `ON CONFLICT` so you can use that syntax instead, for
`DO NOTHING` and `DO UPDATE SET`. In addition,
`INSERT OR REPLACE INTO` can be written using just `REPLACE INTO`
(see below).
Issue [#448](https://github.com/seancorfield/honeysql/issues/448)
has more background on this.

`REPLACE INTO`, while specific to MySQL and SQLite, is supported as
a core part of the DSL, as `:replace-into`, as of 2.4.969.
