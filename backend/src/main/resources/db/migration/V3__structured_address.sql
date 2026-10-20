-- Customer address split into the fields certified invoicing APIs expect.
-- The old free-text address moves into the street field so nothing is lost; postal code and locality
-- stay empty for those rows until someone edits the customer.
alter table customer
    add column address_street text,
    add column address_detail text,
    add column postal_code    text,
    add column locality       text,
    add column country        varchar(2);

update customer set address_street = address, country = 'PT' where address is not null and address <> '';

alter table customer drop column address;
