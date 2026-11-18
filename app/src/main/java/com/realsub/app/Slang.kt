package com.realsub.app

/**
 * Sokak ağzı sözlüğü.
 * Her kayıtta dört karşılık var: 0 yumuşak · 1 doğal · 2 sokak ağzı · 3 tam sansürsüz.
 * Hem ücretsiz motorda doğrudan kullanılır, hem yapay zekâya kural olarak verilir.
 */
object Slang {

    private val rows: List<Pair<String, List<String>>> = listOf(
        "what the fuck|wtf" to listOf("bu da ne", "bu da ne böyle", "bu ne lan", "bu ne amına koyayım"),
        "shut the fuck up|stfu" to listOf("sessiz ol", "kes sesini", "kapa çeneni lan", "kapa o lanet çeneni"),
        "get the fuck out" to listOf("çık dışarı", "defol", "siktir git buradan", "siktir olup git"),
        "are you fucking kidding me" to listOf("şaka mı bu", "dalga mı geçiyorsun", "benimle kafa mı buluyorsun", "dalga mı geçiyorsun amına koyayım"),
        "i don't give a fuck|i don't give a shit" to listOf("umurumda değil", "hiç umurumda değil", "sikimde değil", "sikimde bile değil"),
        "fuck you|screw you" to listOf("canın cehenneme", "defol", "siktir git", "siktir git amına koyayım"),
        "fuck off|piss off" to listOf("git başımdan", "çek git", "siktir git", "siktir ol git"),
        "fuck it|screw it" to listOf("boş ver", "neyse", "siktir et", "siktir et gitsin"),
        "fucked up" to listOf("berbat", "içler acısı", "boktan", "tam bir boktan iş"),
        "son of a bitch" to listOf("alçak", "aşağılık herif", "orospu çocuğu", "orospu çocuğu"),
        "motherfucker" to listOf("aşağılık herif", "şerefsiz", "orospu çocuğu", "anasını siktiğim"),
        "holy shit|holy crap" to listOf("vay canına", "inanmıyorum", "hay sikeyim", "hassiktir"),
        "bullshit" to listOf("saçmalık", "zırvalık", "zırva", "saçmalık amına koyayım"),
        "no fucking way|no way" to listOf("imkânsız", "asla", "yok artık", "yok amına koyayım"),
        "god damn it|damn it|dammit" to listOf("lanet olsun", "kahretsin", "hay sıçayım", "sikeyim böyle işi"),
        "for fuck's sake|for god's sake" to listOf("yapma ya", "Allah aşkına", "hay Allah'ım ya", "sikeyim ya"),
        "piece of shit" to listOf("beş para etmez", "değersiz şey", "boktan şey", "boktan şey"),
        "kiss my ass|my ass" to listOf("hadi oradan", "sen kime diyorsun", "hadi oradan lan", "götümü ye"),
        "pain in the ass" to listOf("baş belası", "baş belası", "baş belası", "götüme batan bir iş"),
        "badass" to listOf("çok iyi", "efsane", "manyak iyi", "manyak iyi"),
        "dumbass|jackass" to listOf("salak", "aptal herif", "geri zekâlı", "götoş"),
        "smartass" to listOf("ukala", "bilmiş", "ukala herif", "ukala puşt"),
        "shit happens" to listOf("olur böyle şeyler", "olur öyle", "boktan işler olur", "sıçıldı işte"),
        "are you out of your mind|are you nuts" to listOf("aklını mı kaçırdın", "delirdin mi", "kafayı mı yedin", "kafayı mı yedin lan"),
        "back off" to listOf("geri çekil", "uzak dur", "çekil lan", "siktir git"),
        "calm down|take it easy" to listOf("sakin ol", "sakin ol", "sakin ol kanka", "sakin ol lan"),
        "come on|c'mon" to listOf("hadi", "hadi ya", "hadi be", "hadi lan"),
        "give me a break" to listOf("yapma ya", "bırak şu işi", "bırak be", "bırak şu işi lan"),
        "what's up|what's going on" to listOf("ne haber", "naber", "ne var ne yok", "naber lan"),
        "i'm screwed" to listOf("hapı yuttum", "yandım", "sıçtım", "sıçtım batırdım"),
        "get out of here" to listOf("defol", "çık dışarı", "bas git", "siktir git"),
        "big deal" to listOf("aman ne büyük iş", "çok şey sanki", "hadi oradan", "sikimde değil"),
        "hell yeah|damn right" to listOf("kesinlikle", "aynen öyle", "hadi be", "siktir evet"),
        "hell no" to listOf("asla", "kesinlikle hayır", "yok öyle bir şey", "siktir et olmaz"),
        "freaking|frigging" to listOf("lanet", "kahrolası", "kahrolası", "lanet olası"),
        "shut up|be quiet" to listOf("sessiz ol", "sus", "kes sesini", "kapa çeneni"),
        "oh my god|jesus christ" to listOf("aman Tanrım", "Allah'ım", "hay Allah", "Allah'ım ya"),
        "i swear to god" to listOf("yemin ederim", "yemin ederim", "yemin ediyorum lan", "yemin ederim amına koyayım"),
        "you bastard" to listOf("seni gidi", "seni alçak", "seni piç", "seni piç kurusu"),
        "i'm out" to listOf("ben yokum", "bu işte yokum", "bu işte yokum", "bu işte yokum lan"),
        "in deep shit" to listOf("başı dertte", "başı büyük belada", "boku yemiş", "boku yemiş"),
        "cut the crap" to listOf("saçmalamayı kes", "boş yapma", "zırvalamayı kes", "boş yapma lan"),
        "we're fucked|i'm fucked" to listOf("hapı yuttuk", "işimiz bitti", "sıçtık", "sıçtık amına koyayım"),
        "don't mess with me" to listOf("benimle uğraşma", "benimle uğraşma", "benimle dalga geçme lan", "benimle uğraşma lan"),
        "fucking hell|bloody hell" to listOf("lanet olsun", "kahretsin", "hay sikeyim", "sikeyim böyle hayatı"),
        "fucking|fuckin" to listOf("lanet", "kahrolası", "lanet olası", "sikik"),
        "fuck" to listOf("lanet", "kahretsin", "siktir", "sikeyim"),
        "shitty" to listOf("berbat", "kötü", "boktan", "boktan"),
        "shit" to listOf("bok", "kahretsin", "bok", "sıçtık"),
        "crap" to listOf("saçmalık", "boş laf", "zırva", "bok"),
        "damn|damned" to listOf("lanet", "kahrolası", "lanet olası", "lanet olası"),
        "bitch" to listOf("cadaloz", "sürtük", "kaltak", "orospu"),
        "bastard" to listOf("alçak", "şerefsiz", "piç", "piç kurusu"),
        "asshole|arsehole" to listOf("pislik", "şerefsiz", "göt herif", "götveren"),
        "dick|prick" to listOf("pislik", "şerefsiz", "yavşak", "sikkafalı"),
        "pussy" to listOf("korkak", "ödlek", "korkak tavuk", "amcık ağızlı"),
        "slut|whore" to listOf("hafifmeşrep", "sürtük", "sürtük", "orospu"),
        "idiot|moron" to listOf("aptal", "salak", "gerizekâlı", "götoş"),
        "jerk|creep" to listOf("pislik", "ahmak", "yavşak", "şerefsiz"),
        "scumbag" to listOf("aşağılık", "pislik", "şerefsiz", "puşt"),
        "loser" to listOf("zavallı", "ezik", "ezik", "götlek"),
        "crazy|insane" to listOf("deli", "çılgın", "manyak", "kafayı sikmiş"),
        "coward" to listOf("korkak", "ödlek", "korkak", "götü yemeyen"),
        "snitch|rat" to listOf("muhbir", "ispiyoncu", "ispiyoncu", "ispiyoncu puşt"),
        "dude|buddy|mate" to listOf("dostum", "ahbap", "kanka", "lan"),
        "cops" to listOf("polis", "polis", "aynasız", "aynasız"),
        "awesome|amazing" to listOf("harika", "muhteşem", "efsane", "manyak iyi"),
        "whatever" to listOf("her neyse", "boş ver", "her neyse", "sikimde değil"),
        "hurry up" to listOf("acele et", "çabuk ol", "kıpırda", "kıpırda lan"),
        "watch out|look out" to listOf("dikkat et", "dikkat", "dikkat lan", "dikkat et lan"),
        "kill" to listOf("öldürmek", "öldürmek", "haklamak", "gebertmek"),
        "gross|disgusting" to listOf("iğrenç", "mide bulandırıcı", "iğrenç", "bok gibi")
    )

    /** Kurallar, uzun kalıp önce gelecek biçimde sıralı. */
    private val entries: List<Pair<String, List<String>>> by lazy {
        val out = ArrayList<Pair<String, List<String>>>()
        for ((keys, vals) in rows) for (k in keys.split("|")) out.add(k.trim() to vals)
        out.sortedByDescending { it.first.length }
    }

    fun pick(vals: List<String>, hard: Int): String =
        vals.getOrElse(hard.coerceIn(0, 3)) { vals.last() }

    /** Metinde geçen kalıpları, yapay zekâya verilecek kural satırı olarak döndürür. */
    fun hintsFor(text: String, hard: Int, limit: Int = 12): List<String> {
        val low = " ${text.lowercase()} "
        val out = ArrayList<String>()
        for ((en, vals) in entries) {
            if (low.contains(en)) {
                out.add("$en -> ${pick(vals, hard)}")
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** Ücretsiz motor için: kalıpları jetonla, çeviri sonrası Türkçesini yerine koy. */
    fun protect(text: String, hard: Int): Pair<String, List<String>> {
        var out = text
        val map = ArrayList<String>()
        for ((en, vals) in entries) {
            val re = Regex("(?i)(^|[^\\p{L}\\p{N}])(" + Regex.escape(en) + ")(?=\$|[^\\p{L}\\p{N}])")
            out = re.replace(out) { m ->
                val i = map.size
                map.add(pick(vals, hard))
                m.groupValues[1] + "⸤" + i + "⸥"
            }
        }
        return out to map
    }

    fun restore(text: String, map: List<String>): String =
        Regex("⸤\\s*(\\d+)\\s*⸥").replace(text) { m ->
            map.getOrElse(m.groupValues[1].toInt()) { "" }
        }
}
