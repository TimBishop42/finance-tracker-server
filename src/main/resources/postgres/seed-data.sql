-- Used for local testing

-- Set search path
SET search_path TO finance;

-- Insert categories
INSERT INTO category (category_name, create_date) VALUES
('Coffee', extract(epoch from now()) * 1000),
('Alcohol', extract(epoch from now()) * 1000),
('Eating Out', extract(epoch from now()) * 1000),
('Chocolate', extract(epoch from now()) * 1000),
('Pet Food', extract(epoch from now()) * 1000),
('Miscellaneous', extract(epoch from now()) * 1000),
('Fuel', extract(epoch from now()) * 1000),
('Bills', extract(epoch from now()) * 1000),
('Baby', extract(epoch from now()) * 1000)
ON CONFLICT (category_name) DO NOTHING;

-- Function to generate random transactions
CREATE OR REPLACE FUNCTION generate_random_transactions(num_transactions integer) RETURNS void AS $$
DECLARE
    categories text[] := ARRAY['Coffee', 'Alcohol', 'Eating Out', 'Chocolate', 'Pet Food', 'Miscellaneous', 'Fuel', 'Bills', 'Baby'];
    category text;
    amount numeric;
    transaction_date timestamp;
    transaction_date_time bigint;
    comment text;
    essential integer;
    create_time bigint;
BEGIN
    FOR i IN 1..num_transactions LOOP
        -- Random category
        category := categories[floor(random() * array_length(categories, 1)) + 1];
        
        -- Random amount between 5 and 200
        amount := round((random() * 195 + 5)::numeric, 2);
        
        -- Random date within last 3 months
        transaction_date := now() - (random() * interval '90 days');
        transaction_date_time := extract(epoch from transaction_date) * 1000;
        
        -- Random comment
        comment := CASE 
            WHEN category = 'Coffee' THEN 'Morning coffee'
            WHEN category = 'Alcohol' THEN 'Weekend drinks'
            WHEN category = 'Eating Out' THEN 'Dinner out'
            WHEN category = 'Chocolate' THEN 'Snacks'
            WHEN category = 'Pet Food' THEN 'Dog food'
            WHEN category = 'Miscellaneous' THEN 'Random purchase'
            WHEN category = 'Fuel' THEN 'Petrol'
            WHEN category = 'Bills' THEN 'Monthly bill'
            WHEN category = 'Baby' THEN 'Baby supplies'
        END;
        
        -- Essential is true for Bills and Fuel, random for others
        essential := CASE 
            WHEN category IN ('Bills', 'Fuel') THEN 1
            ELSE (random() > 0.7)::integer
        END;
        
        -- Create time is same as transaction time
        create_time := transaction_date_time;
        
        -- Insert the transaction
        INSERT INTO transactions (
            category,
            amount,
            transaction_date,
            transaction_date_time,
            comment,
            essential,
            create_time
        ) VALUES (
            category,
            amount,
            to_char(transaction_date, 'YYYY-MM-DD'),
            transaction_date_time,
            comment,
            essential,
            create_time
        );
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Generate 50 random transactions
SELECT generate_random_transactions(50);

-- Clean up
DROP FUNCTION generate_random_transactions(integer); 