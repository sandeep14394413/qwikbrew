CREATE TABLE IF NOT EXISTS menu_items (
    id           VARCHAR(36)   PRIMARY KEY,
    name         VARCHAR(255)  NOT NULL,
    description  TEXT,
    price        NUMERIC(8,2)  NOT NULL,
    category     VARCHAR(50)   NOT NULL,
    emoji        VARCHAR(10)   NOT NULL,
    vegetarian   BOOLEAN       NOT NULL DEFAULT TRUE,
    spicy        BOOLEAN       NOT NULL DEFAULT FALSE,
    available    BOOLEAN       NOT NULL DEFAULT TRUE,
    prep_minutes INTEGER       NOT NULL DEFAULT 10,
    calories     INTEGER       NOT NULL DEFAULT 0,
    featured     BOOLEAN       NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS menu_item_tags (
    item_id VARCHAR(36) REFERENCES menu_items(id) ON DELETE CASCADE,
    tag     VARCHAR(100)
);
CREATE TABLE IF NOT EXISTS menu_item_addons (
    item_id     VARCHAR(36) REFERENCES menu_items(id) ON DELETE CASCADE,
    name        VARCHAR(100),
    extra_price NUMERIC(6,2)
);
CREATE INDEX IF NOT EXISTS idx_menu_category  ON menu_items(category);
CREATE INDEX IF NOT EXISTS idx_menu_available ON menu_items(available);

-- Seed initial menu
INSERT INTO menu_items(id,name,description,price,category,emoji,vegetarian,spicy,available,prep_minutes,calories,featured)
VALUES
('1','Masala Dosa','Crispy dosa with sambar and chutneys',80,'BREAKFAST','🥞',true,false,true,12,320,true),
('2','Cold Brew Coffee','12-hour steeped cold brew with oat milk',120,'DRINKS','☕',true,false,true,3,90,true),
('3','Paneer Wrap','Grilled paneer tikka in whole wheat wrap',110,'LUNCH','🌯',true,false,true,10,420,false),
('4','Veg Burger','Crispy aloo tikki patty with café sauce',130,'LUNCH','🍔',true,true,true,8,480,false),
('5','Vada Pav','Mumbai street snack with chutneys',40,'SNACKS','🍞',true,true,true,5,280,false),
('6','Chocolate Brownie','Warm fudgy brownie with crispy crust',90,'DESSERTS','🍫',true,false,true,2,380,false),
('7','Fresh Lime Soda','Freshly squeezed lime with club soda',60,'DRINKS','🥤',true,false,true,2,50,false),
('8','Upma','South Indian semolina breakfast with vegetables',70,'BREAKFAST','🥣',true,false,true,10,280,false);

INSERT INTO menu_item_tags(item_id,tag) VALUES
('1','Veg'),('1','Bestseller'),
('2','Vegetarian'),('2','Cold'),
('3','Veg'),('3','High Protein'),
('4','Veg'),('4','Spicy'),
('5','Veg'),('5','Street Food'),
('6','Vegetarian'),('6','Sweet'),
('7','Veg'),('7','Refreshing'),
('8','Veg'),('8','Light');
