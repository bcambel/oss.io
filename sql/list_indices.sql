select
    t.relname as table_name,
    i.relname as index_name,
    array_to_string(array_agg(a.attname), ', ') as column_names
from
    pg_class t,
    pg_class i,
    pg_index ix,
    pg_attribute a
where
    t.oid = ix.indrelid
    and i.oid = ix.indexrelid
    and a.attrelid = t.oid
    and a.attnum = ANY(ix.indkey)
    and t.relkind = 'r'
    and t.relname like 'github_project%'
group by
    t.relname,
    i.relname
order by
    t.relname,
    i.relname;
