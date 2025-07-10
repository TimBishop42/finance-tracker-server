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
    coffee_businesses text[] := ARRAY['Starbucks', 'Costa Coffee', 'Caffè Nero', 'Pret A Manger', 'Tim Hortons', 'Dunkin'''];
    alcohol_businesses text[] := ARRAY['Wetherspoons', 'The Red Lion', 'Tesco', 'Sainsbury''s', 'ASDA', 'Morrisons'];
    eating_out_businesses text[] := ARRAY['McDonald''s', 'KFC', 'Pizza Hut', 'Subway', 'Nando''s', 'Wagamama', 'Five Guys'];
    chocolate_businesses text[] := ARRAY['Tesco', 'Sainsbury''s', 'ASDA', 'Morrisons', 'WHSmith', 'Boots'];
    pet_food_businesses text[] := ARRAY['Pets at Home', 'Petco', 'PetSmart', 'Tesco', 'ASDA', 'Morrisons'];
    misc_businesses text[] := ARRAY['Amazon', 'Argos', 'John Lewis', 'Currys', 'B&Q', 'Homebase', 'Wilko'];
    fuel_businesses text[] := ARRAY['Shell', 'BP', 'Esso', 'Texaco', 'Sainsbury''s Petrol', 'Tesco Petrol'];
    bill_businesses text[] := ARRAY['British Gas', 'EDF Energy', 'E.ON', 'Thames Water', 'BT', 'Sky', 'Virgin Media'];
    baby_businesses text[] := ARRAY['Mothercare', 'Boots', 'Tesco', 'ASDA', 'Sainsbury''s', 'Mamas & Papas'];
    category text;
    amount numeric;
    transaction_date timestamp;
    transaction_date_time bigint;
    comment text;
    business_name text;
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
        
        -- Random business name based on category
        business_name := CASE 
            WHEN category = 'Coffee' THEN coffee_businesses[floor(random() * array_length(coffee_businesses, 1)) + 1]
            WHEN category = 'Alcohol' THEN alcohol_businesses[floor(random() * array_length(alcohol_businesses, 1)) + 1]
            WHEN category = 'Eating Out' THEN eating_out_businesses[floor(random() * array_length(eating_out_businesses, 1)) + 1]
            WHEN category = 'Chocolate' THEN chocolate_businesses[floor(random() * array_length(chocolate_businesses, 1)) + 1]
            WHEN category = 'Pet Food' THEN pet_food_businesses[floor(random() * array_length(pet_food_businesses, 1)) + 1]
            WHEN category = 'Miscellaneous' THEN misc_businesses[floor(random() * array_length(misc_businesses, 1)) + 1]
            WHEN category = 'Fuel' THEN fuel_businesses[floor(random() * array_length(fuel_businesses, 1)) + 1]
            WHEN category = 'Bills' THEN bill_businesses[floor(random() * array_length(bill_businesses, 1)) + 1]
            WHEN category = 'Baby' THEN baby_businesses[floor(random() * array_length(baby_businesses, 1)) + 1]
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
            business_name,
            create_time
        ) VALUES (
            category,
            amount,
            to_char(transaction_date, 'YYYY-MM-DD'),
            transaction_date_time,
            comment,
            essential,
            business_name,
            create_time
        );
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Generate 50 random transactions
SELECT generate_random_transactions(50);

-- Clean up
DROP FUNCTION generate_random_transactions(integer); 