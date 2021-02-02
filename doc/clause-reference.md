# SQL Clauses Supported

This section lists all the SQL clauses that HoneySQL
supports out of the box, in the order that they are
processed for formatting.

Clauses can be specified as keywords or symbols. Use
`-` in the clause name where the formatted SQL would have
a space (e.g., `:left-join` is formatted as `LEFT JOIN`).

Except as noted, these clauses apply to all the SQL
dialects that HoneySQL supports.

## nest
## with, with-recursive
## intersect, union, union-all, except, except-all
## select, select-distinct
## insert-into
## update
## delete, delete-from
## truncate
## columns
## set (ANSI)
## from
## using
## join, left-join, right-join, inner-join, outer-join, full-join
## cross-join
## set (MySQL)
## where
## group-by
## having
## order-by
## limit, offset (MySQL)
## for
## lock (MySQL)
## values
## on-conflict, on-constraint, do-nothing, do-update-set
## returning