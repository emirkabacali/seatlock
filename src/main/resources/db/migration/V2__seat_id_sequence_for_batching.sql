-- Let Hibernate reserve seat ids in blocks of 50 (pooled optimizer) so that bulk seat
-- generation can use JDBC batch inserts. Must match allocationSize on Seat.id.
ALTER SEQUENCE seats_id_seq INCREMENT BY 50;
