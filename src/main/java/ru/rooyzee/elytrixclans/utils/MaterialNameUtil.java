package ru.rooyzee.elytrixclans.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

/**
 * Русские названия предметов.
 *
 * Нужны там, где название берётся из материала: предмет без своего имени, положенный
 * в редактор магазина, иначе попадал бы в витрину как «Diamond sword».
 *
 * Работает в три шага:
 *  1. Точное совпадение по словарю — для всего, что переводится не по правилам
 *     (ELYTRA, TOTEM_OF_UNDYING, названия блоков и еды).
 *  2. Правило «материал + предмет»: DIAMOND_SWORD → «Алмазный меч». Прилагательное
 *     согласуется с родом существительного, поэтому получается «Алмазная кирка»,
 *     но «Алмазные ботинки».
 *  3. Если ничего не подошло — исходное имя с заглавной буквы, как было раньше.
 */
public final class MaterialNameUtil {

    /** Род существительного: нужен, чтобы согласовать прилагательное. */
    private enum Gender { M, F, N, PL }

    private static final class Noun {
        private final String word;
        private final Gender gender;

        private Noun(String word, Gender gender) {
            this.word = word;
            this.gender = gender;
        }
    }

    /** Прилагательные по родам: мужской, женский, средний, множественное число. */
    private static final class Adjective {
        private final String m;
        private final String f;
        private final String n;
        private final String pl;

        private Adjective(String m, String f, String n, String pl) {
            this.m = m;
            this.f = f;
            this.n = n;
            this.pl = pl;
        }

        private String forGender(Gender gender) {
            switch (gender) {
                case F: return f;
                case N: return n;
                case PL: return pl;
                default: return m;
            }
        }
    }

    private static final Map<String, String> EXACT = new HashMap<>();
    private static final Map<String, Noun> NOUNS = new HashMap<>();
    private static final Map<String, Adjective> ADJECTIVES = new HashMap<>();

    private MaterialNameUtil() {
    }

    /** Русское название материала. Никогда не возвращает null. */
    public static String of(Material material) {
        if (material == null) return "Предмет";
        return of(material.name());
    }

    public static String of(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "Предмет";
        String key = rawName.toUpperCase(Locale.ROOT);

        String exact = EXACT.get(key);
        if (exact != null) return exact;

        String composed = compose(key);
        if (composed != null) return composed;

        // Ничего не подошло: аккуратный вид английского названия.
        String raw = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /**
     * Сборка по правилу «прилагательное + существительное».
     * Существительным считается самый длинный подходящий хвост названия.
     */
    private static String compose(String key) {
        String[] parts = key.split("_");
        if (parts.length < 2) return null;

        // Идём от самого длинного хвоста к короткому: STONE_PRESSURE_PLATE должен
        // опознаться как «нажимная плита», а не как «плита».
        for (int start = 1; start < parts.length; start++) {
            String tail = join(parts, start);
            Noun noun = NOUNS.get(tail);
            if (noun == null) continue;

            String head = join(parts, 0, start);
            Adjective adjective = ADJECTIVES.get(head);
            if (adjective == null) continue;

            return capitalize(adjective.forGender(noun.gender) + " " + noun.word);
        }
        return null;
    }

    private static String join(String[] parts, int from) {
        return join(parts, from, parts.length);
    }

    private static String join(String[] parts, int from, int to) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (builder.length() > 0) builder.append('_');
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static void noun(String key, String word, Gender gender) {
        NOUNS.put(key, new Noun(word, gender));
    }

    private static void adj(String key, String m, String f, String n, String pl) {
        ADJECTIVES.put(key, new Adjective(m, f, n, pl));
    }

    static {
        // ---- Существительные: предметы, которые бывают из разных материалов ----
        noun("SWORD", "меч", Gender.M);
        noun("PICKAXE", "кирка", Gender.F);
        noun("AXE", "топор", Gender.M);
        noun("SHOVEL", "лопата", Gender.F);
        noun("HOE", "мотыга", Gender.F);
        noun("HELMET", "шлем", Gender.M);
        noun("CHESTPLATE", "нагрудник", Gender.M);
        noun("LEGGINGS", "поножи", Gender.PL);
        noun("BOOTS", "ботинки", Gender.PL);
        noun("INGOT", "слиток", Gender.M);
        noun("NUGGET", "кусочек", Gender.M);
        noun("ORE", "руда", Gender.F);
        noun("BLOCK", "блок", Gender.M);
        noun("SCRAP", "лом", Gender.M);
        noun("PLANKS", "доски", Gender.PL);
        noun("LOG", "бревно", Gender.N);
        noun("WOOD", "древесина", Gender.F);
        noun("STRIPPED_LOG", "обтёсанное бревно", Gender.N);
        noun("STRIPPED_WOOD", "обтёсанная древесина", Gender.F);
        noun("LEAVES", "листва", Gender.F);
        noun("SAPLING", "саженец", Gender.M);
        noun("SLAB", "плита", Gender.F);
        noun("STAIRS", "ступеньки", Gender.PL);
        noun("FENCE", "забор", Gender.M);
        noun("FENCE_GATE", "калитка", Gender.F);
        noun("DOOR", "дверь", Gender.F);
        noun("TRAPDOOR", "люк", Gender.M);
        noun("BUTTON", "кнопка", Gender.F);
        noun("PRESSURE_PLATE", "нажимная плита", Gender.F);
        noun("SIGN", "табличка", Gender.F);
        noun("BOAT", "лодка", Gender.F);
        noun("WALL", "стенка", Gender.F);
        noun("BRICKS", "кирпичи", Gender.PL);
        noun("BRICK", "кирпич", Gender.M);
        noun("TILES", "плитка", Gender.F);
        noun("WOOL", "шерсть", Gender.F);
        noun("CARPET", "ковёр", Gender.M);
        noun("BED", "кровать", Gender.F);
        noun("BANNER", "флаг", Gender.M);
        noun("DYE", "краситель", Gender.M);
        noun("TERRACOTTA", "терракота", Gender.F);
        noun("CONCRETE", "бетон", Gender.M);
        noun("CONCRETE_POWDER", "бетонная пыль", Gender.F);
        noun("GLASS", "стекло", Gender.N);
        noun("GLASS_PANE", "стеклянная панель", Gender.F);
        noun("SHULKER_BOX", "шалкеровый ящик", Gender.M);
        noun("CANDLE", "свеча", Gender.F);
        noun("SEEDS", "семена", Gender.PL);
        noun("BOOTS_TRIM", "ботинки", Gender.PL);
        noun("HORSE_ARMOR", "конская броня", Gender.F);
        noun("APPLE", "яблоко", Gender.N);
        noun("CARROT", "морковь", Gender.F);
        noun("PICKAXE_HEAD", "кирка", Gender.F);

        // ---- Прилагательные по материалу ----
        adj("WOODEN", "деревянный", "деревянная", "деревянное", "деревянные");
        adj("OAK", "дубовый", "дубовая", "дубовое", "дубовые");
        adj("SPRUCE", "еловый", "еловая", "еловое", "еловые");
        adj("BIRCH", "берёзовый", "берёзовая", "берёзовое", "берёзовые");
        adj("JUNGLE", "тропический", "тропическая", "тропическое", "тропические");
        adj("ACACIA", "акациевый", "акациевая", "акациевое", "акациевые");
        adj("DARK_OAK", "тёмно-дубовый", "тёмно-дубовая", "тёмно-дубовое", "тёмно-дубовые");
        adj("CRIMSON", "багровый", "багровая", "багровое", "багровые");
        adj("WARPED", "искажённый", "искажённая", "искажённое", "искажённые");
        adj("STONE", "каменный", "каменная", "каменное", "каменные");
        adj("COBBLESTONE", "булыжниковый", "булыжниковая", "булыжниковое", "булыжниковые");
        adj("IRON", "железный", "железная", "железное", "железные");
        adj("GOLD", "золотой", "золотая", "золотое", "золотые");
        adj("GOLDEN", "золотой", "золотая", "золотое", "золотые");
        adj("DIAMOND", "алмазный", "алмазная", "алмазное", "алмазные");
        adj("NETHERITE", "незеритовый", "незеритовая", "незеритовое", "незеритовые");
        adj("LEATHER", "кожаный", "кожаная", "кожаное", "кожаные");
        adj("CHAINMAIL", "кольчужный", "кольчужная", "кольчужное", "кольчужные");
        adj("COAL", "угольный", "угольная", "угольное", "угольные");
        adj("COPPER", "медный", "медная", "медное", "медные");
        adj("LAPIS", "лазуритовый", "лазуритовая", "лазуритовое", "лазуритовые");
        adj("REDSTONE", "редстоуновый", "редстоуновая", "редстоуновое", "редстоуновые");
        adj("EMERALD", "изумрудный", "изумрудная", "изумрудное", "изумрудные");
        adj("QUARTZ", "кварцевый", "кварцевая", "кварцевое", "кварцевые");
        adj("NETHER", "адский", "адская", "адское", "адские");
        adj("END_STONE", "эндерняковый", "эндерняковая", "эндерняковое", "эндерняковые");
        adj("PURPUR", "пурпурный", "пурпурная", "пурпурное", "пурпурные");
        adj("PRISMARINE", "призмариновый", "призмариновая", "призмариновое", "призмариновые");
        adj("SANDSTONE", "песчаниковый", "песчаниковая", "песчаниковое", "песчаниковые");
        adj("BLACKSTONE", "базальтовый", "базальтовая", "базальтовое", "базальтовые");
        adj("GLOWSTONE", "светокаменный", "светокаменная", "светокаменное", "светокаменные");
        adj("ANDESITE", "андезитовый", "андезитовая", "андезитовое", "андезитовые");
        adj("DIORITE", "диоритовый", "диоритовая", "диоритовое", "диоритовые");
        adj("GRANITE", "гранитный", "гранитная", "гранитное", "гранитные");
        adj("BRICK", "кирпичный", "кирпичная", "кирпичное", "кирпичные");
        adj("SNOW", "снежный", "снежная", "снежное", "снежные");
        adj("ICE", "ледяной", "ледяная", "ледяное", "ледяные");
        adj("CLAY", "глиняный", "глиняная", "глиняное", "глиняные");
        adj("BONE", "костяной", "костяная", "костяное", "костяные");
        adj("SLIME", "слизневый", "слизневая", "слизневое", "слизневые");
        adj("HONEY", "медовый", "медовая", "медовое", "медовые");
        adj("MAGMA", "магмовый", "магмовая", "магмовое", "магмовые");
        adj("SOUL", "душевный", "душевная", "душевное", "душевные");
        adj("WHITE", "белый", "белая", "белое", "белые");
        adj("ORANGE", "оранжевый", "оранжевая", "оранжевое", "оранжевые");
        adj("MAGENTA", "пурпурный", "пурпурная", "пурпурное", "пурпурные");
        adj("LIGHT_BLUE", "голубой", "голубая", "голубое", "голубые");
        adj("YELLOW", "жёлтый", "жёлтая", "жёлтое", "жёлтые");
        adj("LIME", "лаймовый", "лаймовая", "лаймовое", "лаймовые");
        adj("PINK", "розовый", "розовая", "розовое", "розовые");
        adj("GRAY", "серый", "серая", "серое", "серые");
        adj("LIGHT_GRAY", "светло-серый", "светло-серая", "светло-серое", "светло-серые");
        adj("CYAN", "бирюзовый", "бирюзовая", "бирюзовое", "бирюзовые");
        adj("PURPLE", "фиолетовый", "фиолетовая", "фиолетовое", "фиолетовые");
        adj("BLUE", "синий", "синяя", "синее", "синие");
        adj("BROWN", "коричневый", "коричневая", "коричневое", "коричневые");
        adj("GREEN", "зелёный", "зелёная", "зелёное", "зелёные");
        adj("RED", "красный", "красная", "красное", "красные");
        adj("BLACK", "чёрный", "чёрная", "чёрное", "чёрные");

        // ---- Точные названия ----
        EXACT.put("ELYTRA", "Элитры");
        EXACT.put("TOTEM_OF_UNDYING", "Тотем бессмертия");
        EXACT.put("NETHER_STAR", "Незвезда");
        EXACT.put("BEACON", "Маяк");
        EXACT.put("CONDUIT", "Проводник");
        EXACT.put("SHULKER_SHELL", "Панцирь шалкера");
        EXACT.put("DRAGON_EGG", "Яйцо дракона");
        EXACT.put("DRAGON_BREATH", "Дыхание дракона");
        EXACT.put("END_CRYSTAL", "Кристалл Края");
        EXACT.put("RESPAWN_ANCHOR", "Якорь возрождения");
        EXACT.put("ENDER_PEARL", "Жемчуг Края");
        EXACT.put("ENDER_EYE", "Око Края");
        EXACT.put("EXPERIENCE_BOTTLE", "Пузырёк опыта");
        EXACT.put("ENCHANTED_BOOK", "Зачарованная книга");
        EXACT.put("ENCHANTING_TABLE", "Стол зачаровывания");
        EXACT.put("ANVIL", "Наковальня");
        EXACT.put("CHIPPED_ANVIL", "Повреждённая наковальня");
        EXACT.put("DAMAGED_ANVIL", "Разрушенная наковальня");
        EXACT.put("GRINDSTONE", "Точило");
        EXACT.put("SMITHING_TABLE", "Кузнечный стол");
        EXACT.put("BREWING_STAND", "Варочная стойка");
        EXACT.put("CAULDRON", "Котёл");
        EXACT.put("FURNACE", "Печь");
        EXACT.put("BLAST_FURNACE", "Плавильная печь");
        EXACT.put("SMOKER", "Коптильня");
        EXACT.put("CRAFTING_TABLE", "Верстак");
        EXACT.put("CHEST", "Сундук");
        EXACT.put("ENDER_CHEST", "Эндер-сундук");
        EXACT.put("TRAPPED_CHEST", "Сундук-ловушка");
        EXACT.put("BARREL", "Бочка");
        EXACT.put("HOPPER", "Воронка");
        EXACT.put("DROPPER", "Выбрасыватель");
        EXACT.put("DISPENSER", "Раздатчик");
        EXACT.put("OBSERVER", "Наблюдатель");
        EXACT.put("PISTON", "Поршень");
        EXACT.put("STICKY_PISTON", "Липкий поршень");
        EXACT.put("REDSTONE", "Редстоун");
        EXACT.put("REPEATER", "Повторитель");
        EXACT.put("COMPARATOR", "Компаратор");
        EXACT.put("LEVER", "Рычаг");
        EXACT.put("TORCH", "Факел");
        EXACT.put("SOUL_TORCH", "Душевный факел");
        EXACT.put("LANTERN", "Фонарь");
        EXACT.put("SOUL_LANTERN", "Душевный фонарь");
        EXACT.put("CAMPFIRE", "Костёр");
        EXACT.put("SOUL_CAMPFIRE", "Душевный костёр");
        EXACT.put("TNT", "Динамит");
        EXACT.put("OBSIDIAN", "Обсидиан");
        EXACT.put("CRYING_OBSIDIAN", "Плачущий обсидиан");
        EXACT.put("BEDROCK", "Коренная порода");
        EXACT.put("DIRT", "Земля");
        EXACT.put("GRASS_BLOCK", "Блок травы");
        EXACT.put("PODZOL", "Подзол");
        EXACT.put("MYCELIUM", "Мицелий");
        EXACT.put("FARMLAND", "Грядка");
        EXACT.put("SAND", "Песок");
        EXACT.put("RED_SAND", "Красный песок");
        EXACT.put("GRAVEL", "Гравий");
        EXACT.put("FLINT", "Кремень");
        EXACT.put("FLINT_AND_STEEL", "Огниво");
        EXACT.put("CLAY_BALL", "Глина");
        EXACT.put("STICK", "Палка");
        EXACT.put("STRING", "Нить");
        EXACT.put("FEATHER", "Перо");
        EXACT.put("LEATHER", "Кожа");
        EXACT.put("BONE", "Кость");
        EXACT.put("BONE_MEAL", "Костная мука");
        EXACT.put("GUNPOWDER", "Порох");
        EXACT.put("BLAZE_ROD", "Огненный стержень");
        EXACT.put("BLAZE_POWDER", "Огненный порошок");
        EXACT.put("GHAST_TEAR", "Слеза гаста");
        EXACT.put("MAGMA_CREAM", "Магмовый крем");
        EXACT.put("SLIME_BALL", "Слизь");
        EXACT.put("SPIDER_EYE", "Паучий глаз");
        EXACT.put("FERMENTED_SPIDER_EYE", "Ферментированный паучий глаз");
        EXACT.put("ROTTEN_FLESH", "Гнилая плоть");
        EXACT.put("PHANTOM_MEMBRANE", "Мембрана фантома");
        EXACT.put("RABBIT_FOOT", "Кроличья лапка");
        EXACT.put("RABBIT_HIDE", "Кроличья шкурка");
        EXACT.put("NAUTILUS_SHELL", "Раковина наутилуса");
        EXACT.put("HEART_OF_THE_SEA", "Сердце моря");
        EXACT.put("PRISMARINE_SHARD", "Осколок призмарина");
        EXACT.put("PRISMARINE_CRYSTALS", "Кристаллы призмарина");
        EXACT.put("SCUTE", "Щиток");
        EXACT.put("INK_SAC", "Мешок с чернилами");
        EXACT.put("GLOW_INK_SAC", "Светящийся мешок с чернилами");
        EXACT.put("PAPER", "Бумага");
        EXACT.put("BOOK", "Книга");
        EXACT.put("WRITABLE_BOOK", "Книга с пером");
        EXACT.put("WRITTEN_BOOK", "Подписанная книга");
        EXACT.put("MAP", "Карта");
        EXACT.put("FILLED_MAP", "Заполненная карта");
        EXACT.put("COMPASS", "Компас");
        EXACT.put("CLOCK", "Часы");
        EXACT.put("SPYGLASS", "Подзорная труба");
        EXACT.put("BUCKET", "Ведро");
        EXACT.put("WATER_BUCKET", "Ведро с водой");
        EXACT.put("LAVA_BUCKET", "Ведро с лавой");
        EXACT.put("MILK_BUCKET", "Ведро с молоком");
        EXACT.put("BOW", "Лук");
        EXACT.put("CROSSBOW", "Арбалет");
        EXACT.put("ARROW", "Стрела");
        EXACT.put("SPECTRAL_ARROW", "Спектральная стрела");
        EXACT.put("TIPPED_ARROW", "Стрела с эффектом");
        EXACT.put("TRIDENT", "Трезубец");
        EXACT.put("SHIELD", "Щит");
        EXACT.put("FISHING_ROD", "Удочка");
        EXACT.put("CARROT_ON_A_STICK", "Морковь на удочке");
        EXACT.put("SHEARS", "Ножницы");
        EXACT.put("SADDLE", "Седло");
        EXACT.put("NAME_TAG", "Бирка");
        EXACT.put("LEAD", "Поводок");
        EXACT.put("FIREWORK_ROCKET", "Фейерверк");
        EXACT.put("FIREWORK_STAR", "Звезда фейерверка");
        EXACT.put("POTION", "Зелье");
        EXACT.put("SPLASH_POTION", "Взрывное зелье");
        EXACT.put("LINGERING_POTION", "Оседающее зелье");
        EXACT.put("GLASS_BOTTLE", "Стеклянная бутылка");
        EXACT.put("GOLDEN_APPLE", "Золотое яблоко");
        EXACT.put("ENCHANTED_GOLDEN_APPLE", "Зачарованное золотое яблоко");
        EXACT.put("APPLE", "Яблоко");
        EXACT.put("BREAD", "Хлеб");
        EXACT.put("WHEAT", "Пшеница");
        EXACT.put("WHEAT_SEEDS", "Семена пшеницы");
        EXACT.put("CARROT", "Морковь");
        EXACT.put("POTATO", "Картофель");
        EXACT.put("BAKED_POTATO", "Печёный картофель");
        EXACT.put("POISONOUS_POTATO", "Ядовитый картофель");
        EXACT.put("BEETROOT", "Свёкла");
        EXACT.put("BEETROOT_SOUP", "Свекольный суп");
        EXACT.put("MUSHROOM_STEW", "Грибная похлёбка");
        EXACT.put("RABBIT_STEW", "Тушёный кролик");
        EXACT.put("SUSPICIOUS_STEW", "Подозрительный суп");
        EXACT.put("BEEF", "Сырая говядина");
        EXACT.put("COOKED_BEEF", "Стейк");
        EXACT.put("PORKCHOP", "Сырая свинина");
        EXACT.put("COOKED_PORKCHOP", "Жареная свинина");
        EXACT.put("CHICKEN", "Сырая курятина");
        EXACT.put("COOKED_CHICKEN", "Жареная курятина");
        EXACT.put("MUTTON", "Сырая баранина");
        EXACT.put("COOKED_MUTTON", "Жареная баранина");
        EXACT.put("RABBIT", "Сырая крольчатина");
        EXACT.put("COOKED_RABBIT", "Жареная крольчатина");
        EXACT.put("COD", "Сырая треска");
        EXACT.put("COOKED_COD", "Жареная треска");
        EXACT.put("SALMON", "Сырой лосось");
        EXACT.put("COOKED_SALMON", "Жареный лосось");
        EXACT.put("TROPICAL_FISH", "Тропическая рыба");
        EXACT.put("PUFFERFISH", "Иглобрюх");
        EXACT.put("COOKIE", "Печенье");
        EXACT.put("CAKE", "Торт");
        EXACT.put("PUMPKIN_PIE", "Тыквенный пирог");
        EXACT.put("MELON_SLICE", "Ломтик арбуза");
        EXACT.put("SWEET_BERRIES", "Сладкие ягоды");
        EXACT.put("HONEY_BOTTLE", "Бутылочка мёда");
        EXACT.put("SUGAR", "Сахар");
        EXACT.put("SUGAR_CANE", "Сахарный тростник");
        EXACT.put("CHORUS_FRUIT", "Плод коруса");
        EXACT.put("DRIED_KELP", "Сушёные водоросли");
        EXACT.put("COAL", "Уголь");
        EXACT.put("CHARCOAL", "Древесный уголь");
        EXACT.put("DIAMOND", "Алмаз");
        EXACT.put("EMERALD", "Изумруд");
        EXACT.put("LAPIS_LAZULI", "Лазурит");
        EXACT.put("QUARTZ", "Кварц");
        EXACT.put("AMETHYST_SHARD", "Осколок аметиста");
        EXACT.put("NETHERITE_SCRAP", "Незеритовый лом");
        EXACT.put("ANCIENT_DEBRIS", "Древние обломки");
        EXACT.put("GLOWSTONE_DUST", "Светокаменная пыль");
        EXACT.put("GLOWSTONE", "Светокамень");
        EXACT.put("SPONGE", "Губка");
        EXACT.put("WET_SPONGE", "Мокрая губка");
        EXACT.put("COBWEB", "Паутина");
        EXACT.put("LADDER", "Лестница");
        EXACT.put("SCAFFOLDING", "Леса");
        EXACT.put("RAIL", "Рельсы");
        EXACT.put("POWERED_RAIL", "Электрические рельсы");
        EXACT.put("DETECTOR_RAIL", "Нажимные рельсы");
        EXACT.put("ACTIVATOR_RAIL", "Активирующие рельсы");
        EXACT.put("MINECART", "Вагонетка");
        EXACT.put("BOOKSHELF", "Книжная полка");
        EXACT.put("JUKEBOX", "Проигрыватель");
        EXACT.put("NOTE_BLOCK", "Нотный блок");
        EXACT.put("BELL", "Колокол");
        EXACT.put("LODESTONE", "Магнетит");
        EXACT.put("LOOM", "Ткацкий станок");
        EXACT.put("CARTOGRAPHY_TABLE", "Картографический стол");
        EXACT.put("FLETCHING_TABLE", "Стол лучника");
        EXACT.put("STONECUTTER", "Камнерез");
        EXACT.put("COMPOSTER", "Компостер");
        EXACT.put("BEEHIVE", "Улей");
        EXACT.put("BEE_NEST", "Пчелиное гнездо");
        EXACT.put("HONEYCOMB", "Соты");
        EXACT.put("TARGET", "Мишень");
        EXACT.put("NETHERRACK", "Адский камень");
        EXACT.put("SOUL_SAND", "Песок душ");
        EXACT.put("SOUL_SOIL", "Почва душ");
        EXACT.put("BASALT", "Базальт");
        EXACT.put("BLACKSTONE", "Базальт");
        EXACT.put("SHROOMLIGHT", "Грибосвет");
        EXACT.put("NETHER_WART", "Адский нарост");
        EXACT.put("NETHER_WART_BLOCK", "Блок адского нароста");
        EXACT.put("CHORUS_FLOWER", "Цветок коруса");
        EXACT.put("END_ROD", "Стержень Края");
        EXACT.put("SEA_LANTERN", "Морской фонарь");
        EXACT.put("PUMPKIN", "Тыква");
        EXACT.put("CARVED_PUMPKIN", "Вырезанная тыква");
        EXACT.put("JACK_O_LANTERN", "Светильник Джека");
        EXACT.put("MELON", "Арбуз");
        EXACT.put("HAY_BLOCK", "Стог сена");
        EXACT.put("SHULKER_BOX", "Шалкеровый ящик");
        EXACT.put("ARMOR_STAND", "Стойка для брони");
        EXACT.put("ITEM_FRAME", "Рамка");
        EXACT.put("PAINTING", "Картина");
        EXACT.put("FLOWER_POT", "Горшок");
        EXACT.put("PLAYER_HEAD", "Голова игрока");
        EXACT.put("GOLD_INGOT", "Золотой слиток");
        EXACT.put("IRON_INGOT", "Железный слиток");
        EXACT.put("NETHERITE_INGOT", "Незеритовый слиток");
        EXACT.put("COPPER_INGOT", "Медный слиток");
        EXACT.put("GLASS", "Стекло");
        EXACT.put("GLASS_PANE", "Стеклянная панель");
        EXACT.put("COBBLESTONE", "Булыжник");
        EXACT.put("MOSSY_COBBLESTONE", "Замшелый булыжник");
        EXACT.put("STONE", "Камень");
        EXACT.put("SMOOTH_STONE", "Гладкий камень");
        EXACT.put("DEEPSLATE", "Глубинный сланец");
        EXACT.put("CALCITE", "Кальцит");
        EXACT.put("TUFF", "Туф");
        EXACT.put("DRIPSTONE_BLOCK", "Капельный камень");
        EXACT.put("MOSS_BLOCK", "Мох");
        EXACT.put("WOOL", "Шерсть");
        EXACT.put("CARPET", "Ковёр");
        EXACT.put("TERRACOTTA", "Терракота");
        EXACT.put("BRICKS", "Кирпичи");
        EXACT.put("BRICK", "Кирпич");
        EXACT.put("STONE_BRICKS", "Каменные кирпичи");
        EXACT.put("NETHER_BRICKS", "Адские кирпичи");
        EXACT.put("QUARTZ_BLOCK", "Кварцевый блок");
        EXACT.put("SANDSTONE", "Песчаник");
        EXACT.put("RED_SANDSTONE", "Красный песчаник");
        EXACT.put("PRISMARINE", "Призмарин");
        EXACT.put("PURPUR_BLOCK", "Пурпурный блок");
        EXACT.put("END_STONE", "Камень Края");
        EXACT.put("END_STONE_BRICKS", "Кирпичи Края");
        EXACT.put("ICE", "Лёд");
        EXACT.put("PACKED_ICE", "Плотный лёд");
        EXACT.put("BLUE_ICE", "Голубой лёд");
        EXACT.put("SNOW", "Снег");
        EXACT.put("SNOW_BLOCK", "Снежный блок");
        EXACT.put("CLAY", "Глина");
        EXACT.put("SLIME_BLOCK", "Блок слизи");
        EXACT.put("HONEY_BLOCK", "Медовый блок");
        EXACT.put("MAGMA_BLOCK", "Магмовый блок");
        EXACT.put("SEA_PICKLE", "Морской огурец");
        EXACT.put("KELP", "Ламинария");
        EXACT.put("VINE", "Лоза");
        EXACT.put("LILY_PAD", "Кувшинка");
        EXACT.put("CACTUS", "Кактус");
        EXACT.put("BAMBOO", "Бамбук");
        EXACT.put("DEAD_BUSH", "Сухой куст");
        EXACT.put("FERN", "Папоротник");
        EXACT.put("SPONGE", "Губка");
        EXACT.put("EGG", "Яйцо");
        EXACT.put("SNOWBALL", "Снежок");
        EXACT.put("GOLD_NUGGET", "Золотой самородок");
        EXACT.put("IRON_NUGGET", "Железный самородок");
        EXACT.put("BARRIER", "Барьер");
        EXACT.put("AIR", "Воздух");
    }
}
