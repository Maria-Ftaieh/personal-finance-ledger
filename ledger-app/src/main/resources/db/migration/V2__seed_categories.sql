-- The seed taxonomy from SPEC §5.3. Ids are ASCII slugs so they are safe in a URL;
-- the Turkish name is the display name and goes through the frontend's i18n layer.

insert into categories (id, display_name, is_system, sort_order) values
    ('yemek',    'Yemek',    false,  1),
    ('ulasim',   'Ulaşım',   false,  2),
    ('dijital',  'Dijital',  false,  3),
    ('eglence',  'Eğlence',  false,  4),
    ('giyim',    'Giyim',    false,  5),
    ('saglik',   'Sağlık',   false,  6),
    ('konut',    'Konut',    false,  7),
    ('egitim',   'Eğitim',   false,  8),
    ('seyahat',  'Seyahat',  false,  9),
    ('finansal', 'Finansal', true,  10),
    ('diger',    'Diğer',    true,  11);

insert into subcategories (id, category_id, display_name, sort_order) values
    ('yemek.kahve',            'yemek',    'Kahve',             1),
    ('yemek.fast_food',        'yemek',    'Fast food',         2),
    ('yemek.restoran',         'yemek',    'Restoran',          3),
    ('yemek.market',           'yemek',    'Market',            4),

    ('ulasim.akaryakit',       'ulasim',   'Akaryakıt',         1),
    ('ulasim.toplu_tasima',    'ulasim',   'Toplu taşıma',      2),
    ('ulasim.taksi',           'ulasim',   'Taksi',             3),
    ('ulasim.otopark',         'ulasim',   'Otopark',           4),

    ('dijital.abonelik',       'dijital',  'Abonelik',          1),
    ('dijital.uygulama',       'dijital',  'Uygulama',          2),
    ('dijital.oyun',           'dijital',  'Oyun',              3),
    ('dijital.bulut',          'dijital',  'Bulut',             4),

    ('eglence.sinema',         'eglence',  'Sinema',            1),
    ('eglence.etkinlik',       'eglence',  'Etkinlik',          2),
    -- "Kitap" appears under both Eğlence and Eğitim in the spec, so the id is qualified
    -- by its parent; two rows with the same display name are two different subcategories.
    ('eglence.kitap',          'eglence',  'Kitap',             3),

    ('giyim.giyim',            'giyim',    'Giyim',             1),
    ('giyim.ayakkabi',         'giyim',    'Ayakkabı',          2),
    ('giyim.aksesuar',         'giyim',    'Aksesuar',          3),

    ('saglik.eczane',          'saglik',   'Eczane',            1),
    ('saglik.doktor',          'saglik',   'Doktor',            2),
    ('saglik.spor',            'saglik',   'Spor',              3),

    ('konut.kira',             'konut',    'Kira',              1),
    ('konut.faturalar',        'konut',    'Faturalar',         2),
    ('konut.ev_esyasi',        'konut',    'Ev eşyası',         3),

    ('egitim.kurs',            'egitim',   'Kurs',              1),
    ('egitim.kitap',           'egitim',   'Kitap',             2),
    ('egitim.yazilim',         'egitim',   'Yazılım',           3),

    ('seyahat.ucak',           'seyahat',  'Uçak',              1),
    ('seyahat.konaklama',      'seyahat',  'Konaklama',         2),

    ('finansal.kart_ucreti',   'finansal', 'Kart ücreti',       1),
    ('finansal.faiz',          'finansal', 'Faiz',              2),
    ('finansal.komisyon',      'finansal', 'Komisyon',          3),

    ('diger.siniflandirilmamis', 'diger',  'Sınıflandırılmamış', 1);
