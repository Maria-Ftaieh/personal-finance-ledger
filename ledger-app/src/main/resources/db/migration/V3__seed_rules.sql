-- Seeded categorisation rules. SPEC §5.3.
--
-- Patterns are written in the normalised form the engine compares against: ASCII-folded
-- and upper case. The engine normalises a rule's pattern the same way a description is
-- normalised, so 'Kahve Dünyası' would work too; storing the folded form just makes what
-- is actually compared visible in the table.
--
-- Priorities sit in the 1000s. That is a convention, not a partition: nothing stops a
-- user rule from taking priority 1 and outranking every rule below, which is exactly the
-- behaviour §5.3 requires.

insert into categorisation_rules (id, user_id, priority, match_type, pattern, category_id, subcategory_id, user_defined) values
    -- Fees, interest and instalment restructuring go to a system category rather than to
    -- "Sınıflandırılmamış", so they never appear as ordinary spending (SPEC §4.3).
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1000, 'CONTAINS', 'KART UCRETI',     'finansal', 'finansal.kart_ucreti', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1001, 'CONTAINS', 'YILLIK UYELIK',   'finansal', 'finansal.kart_ucreti', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1002, 'CONTAINS', 'AIDAT',           'finansal', 'finansal.kart_ucreti', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1010, 'CONTAINS', 'FAIZ',            'finansal', 'finansal.faiz',        false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1011, 'CONTAINS', 'GECIKME',         'finansal', 'finansal.faiz',        false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1020, 'CONTAINS', 'KOMISYON',        'finansal', 'finansal.komisyon',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1021, 'CONTAINS', 'NAKIT AVANS',     'finansal', 'finansal.komisyon',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1022, 'CONTAINS', 'BSMV',            'finansal', 'finansal.komisyon',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1023, 'CONTAINS', 'TAKSITLENDIRME',  'finansal', 'finansal.komisyon',    false),

    -- Yemek
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1100, 'CONTAINS',    'KAHVE DUNYASI', 'yemek', 'yemek.kahve',     false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1101, 'CONTAINS',    'STARBUCKS',     'yemek', 'yemek.kahve',     false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1102, 'CONTAINS',    'GLORIA JEANS',  'yemek', 'yemek.kahve',     false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1110, 'CONTAINS',    'MIGROS',        'yemek', 'yemek.market',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1111, 'CONTAINS',    'CARREFOUR',     'yemek', 'yemek.market',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1112, 'CONTAINS',    'GETIR',         'yemek', 'yemek.market',    false),
    -- Short brand names use a word-anchored regex so "A101" does not also match an
    -- authorisation code and "BIM" does not match "BIMEKS".
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1113, 'REGEX',       '^(A101|BIM|SOK)\b', 'yemek', 'yemek.market', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1120, 'CONTAINS',    'MCDONALDS',     'yemek', 'yemek.fast_food', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1121, 'CONTAINS',    'BURGER KING',   'yemek', 'yemek.fast_food', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1122, 'CONTAINS',    'DOMINOS',       'yemek', 'yemek.fast_food', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1130, 'CONTAINS',    'YEMEKSEPETI',   'yemek', 'yemek.restoran',  false),

    -- Ulaşım
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1200, 'CONTAINS', 'SHELL',        'ulasim', 'ulasim.akaryakit',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1201, 'CONTAINS', 'OPET',         'ulasim', 'ulasim.akaryakit',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1202, 'CONTAINS', 'PETROL OFISI', 'ulasim', 'ulasim.akaryakit',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1210, 'CONTAINS', 'ISTANBULKART', 'ulasim', 'ulasim.toplu_tasima', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1220, 'CONTAINS', 'BITAKSI',      'ulasim', 'ulasim.taksi',        false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1221, 'CONTAINS', 'UBER',         'ulasim', 'ulasim.taksi',        false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1230, 'CONTAINS', 'OTOPARK',      'ulasim', 'ulasim.otopark',      false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1231, 'CONTAINS', 'ISPARK',       'ulasim', 'ulasim.otopark',      false),

    -- Dijital
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1300, 'CONTAINS', 'SPOTIFY',        'dijital', 'dijital.abonelik',  false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1301, 'CONTAINS', 'NETFLIX',        'dijital', 'dijital.abonelik',  false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1302, 'CONTAINS', 'YOUTUBEPREMIUM', 'dijital', 'dijital.abonelik',  false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1310, 'CONTAINS', 'APPLE COM BILL', 'dijital', 'dijital.uygulama',  false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1311, 'CONTAINS', 'GOOGLE PLAY',    'dijital', 'dijital.uygulama',  false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1320, 'CONTAINS', 'STEAM',          'dijital', 'dijital.oyun',      false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1330, 'CONTAINS', 'ICLOUD',         'dijital', 'dijital.bulut',     false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1331, 'CONTAINS', 'AWS',            'dijital', 'dijital.bulut',     false),

    -- Eğlence
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1400, 'CONTAINS', 'CINEMAXIMUM', 'eglence', 'eglence.sinema',   false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1410, 'CONTAINS', 'BILETIX',     'eglence', 'eglence.etkinlik', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1420, 'CONTAINS', 'IDEFIX',      'eglence', 'eglence.kitap',    false),

    -- Giyim
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1500, 'CONTAINS', 'LC WAIKIKI', 'giyim', 'giyim.giyim',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1501, 'CONTAINS', 'DEFACTO',    'giyim', 'giyim.giyim',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1502, 'CONTAINS', 'ZARA',       'giyim', 'giyim.giyim',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1510, 'CONTAINS', 'FLO MAGAZA', 'giyim', 'giyim.ayakkabi', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1520, 'CONTAINS', 'TRENDYOL',   'giyim', 'giyim.giyim',    false),

    -- Sağlık
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1600, 'CONTAINS', 'ECZANE',     'saglik', 'saglik.eczane', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1610, 'CONTAINS', 'HASTANE',    'saglik', 'saglik.doktor', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1611, 'CONTAINS', 'POLIKLINIK', 'saglik', 'saglik.doktor', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1620, 'CONTAINS', 'MACFIT',     'saglik', 'saglik.spor',   false),

    -- Konut
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1700, 'CONTAINS', 'ELEKTRIK',     'konut', 'konut.faturalar', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1701, 'CONTAINS', 'DOGALGAZ',     'konut', 'konut.faturalar', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1702, 'CONTAINS', 'TURKCELL',     'konut', 'konut.faturalar', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1703, 'CONTAINS', 'VODAFONE',     'konut', 'konut.faturalar', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1704, 'CONTAINS', 'TURK TELEKOM', 'konut', 'konut.faturalar', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1710, 'CONTAINS', 'IKEA',         'konut', 'konut.ev_esyasi', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1711, 'CONTAINS', 'KOCTAS',       'konut', 'konut.ev_esyasi', false),

    -- Eğitim
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1800, 'CONTAINS', 'UDEMY',     'egitim', 'egitim.kurs',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1801, 'CONTAINS', 'COURSERA',  'egitim', 'egitim.kurs',    false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1810, 'CONTAINS', 'JETBRAINS', 'egitim', 'egitim.yazilim', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1811, 'CONTAINS', 'GITHUB',    'egitim', 'egitim.yazilim', false),

    -- Seyahat
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1900, 'CONTAINS', 'TURK HAVA YOLLARI', 'seyahat', 'seyahat.ucak',      false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1901, 'REGEX',    '^THY\b',            'seyahat', 'seyahat.ucak',      false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1902, 'CONTAINS', 'PEGASUS',           'seyahat', 'seyahat.ucak',      false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1910, 'CONTAINS', 'BOOKING COM',       'seyahat', 'seyahat.konaklama', false),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1911, 'CONTAINS', 'AIRBNB',            'seyahat', 'seyahat.konaklama', false);
