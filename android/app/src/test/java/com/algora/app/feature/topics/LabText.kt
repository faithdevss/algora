package com.algora.app.feature.topics

/**
 * Every string a topic's visible lab draws, read by reflection, so tests can compare page content
 * against what the reader actually sees in the lab.
 *
 * It follows `SimulationHost`'s routing: story-lab topic sets first, then the older per-type config
 * maps. Labs are walked across their tabs at each tab's starting parameter; slider positions other
 * than the initial one are not visited. Topics whose lab is none of these (most DSA widgets) return
 * an empty string, and callers fall back to the page text alone.
 */
internal object LabText {

    private val cache = HashMap<String, String>()

    fun of(topicId: String): String = synchronized(cache) {
        cache.getOrPut(topicId) { runCatching { strings(topicId).joinToString("\n") }.getOrDefault("") }
    }

    private fun priv(cls: String, name: String, vararg args: Any): Any? {
        val k = Class.forName("com.algora.app.feature.topics.$cls")
        val m = k.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        return m.invoke(null, *args)
    }

    private fun field(o: Any, n: String): Any? = runCatching {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == n }?.let { it.isAccessible = true; return@runCatching it.get(o) }
            c = c.superclass
        }
        o.javaClass.methods.firstOrNull { it.name == "get" + n.replaceFirstChar { c -> c.uppercase() } && it.parameterCount == 0 }
            ?.let { it.isAccessible = true; it.invoke(o) }
    }.getOrNull()

    /** Visible strings only: raw numeric fields are layout, not text the reader sees. */
    private fun collect(o: Any?, out: MutableList<String>, depth: Int, seen: MutableSet<Int>) {
        if (o == null || depth > 7) return
        when (o) {
            is String -> { if (o.isNotBlank()) out.add(o); return }
            is Number, is Boolean, is Char, is Function<*> -> return
            is Iterable<*> -> { o.forEach { collect(it, out, depth + 1, seen) }; return }
            is Array<*> -> { o.forEach { collect(it, out, depth + 1, seen) }; return }
            is Map<*, *> -> { o.values.forEach { collect(it, out, depth + 1, seen) }; return }
            is Pair<*, *> -> { collect(o.first, out, depth + 1, seen); collect(o.second, out, depth + 1, seen); return }
            is Triple<*, *, *> -> { collect(o.first, out, depth + 1, seen); collect(o.second, out, depth + 1, seen); collect(o.third, out, depth + 1, seen); return }
        }
        if (!o.javaClass.name.startsWith("com.algora")) return
        if (!seen.add(System.identityHashCode(o))) return
        var c: Class<*>? = o.javaClass
        while (c != null && c != Any::class.java) {
            c.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach { f ->
                runCatching { f.isAccessible = true; collect(f.get(o), out, depth + 1, seen) }
            }
            c = c.superclass
        }
    }

    private fun invokeAny(fn: Any, vararg args: Any?): Any? {
        val m = fn.javaClass.methods.first { it.name == "invoke" && it.parameterCount == args.size }
        m.isAccessible = true
        return m.invoke(fn, *args)
    }

    private fun labFrames(lab: Any): List<Any?> {
        if (lab is List<*>) return lab.flatMap { t -> (t?.let { field(it, "frames") } as? List<*>) ?: listOf(t) }
        val tabs = (field(lab, "tabs") as? List<*>)?.size?.coerceAtLeast(1) ?: 1
        val initial = field(lab, "initial")
        val fn = field(lab, "frames") ?: field(lab, "frame") ?: field(lab, "build")
        if (fn is List<*>) return fn
        if (fn !is Function<*>) return listOf(lab)
        val arity = fn.javaClass.methods.filter { it.name == "invoke" }.maxOf { it.parameterCount }
        val out = mutableListOf<Any?>(lab)
        when {
            initial != null && arity == 1 -> out.add(invokeAny(fn, initial))
            arity == 0 -> out.add(invokeAny(fn))
            arity == 1 -> for (t in 0 until tabs) out.add(runCatching { invokeAny(fn, t) }.getOrNull())
            // Story labs disagree on argument order — (tab, param) for deep labs, (param, tab) for
            // ensembles — so both are tried; an out-of-range call simply fails and is skipped.
            arity == 2 -> for (t in 0 until tabs.coerceAtLeast(2)) for (p in 0..24) {
                out.add(runCatching { invokeAny(fn, t, p) }.getOrNull())
                out.add(runCatching { invokeAny(fn, p, t) }.getOrNull())
            }
        }
        return out.flatMap { (it as? List<*>) ?: listOf(it) }
    }

    private fun configFrames(cls: String, mapName: String, id: String): List<Any?>? {
        val k = runCatching { Class.forName("com.algora.app.feature.topics.$cls") }.getOrNull() ?: return null
        val f = k.declaredFields.firstOrNull { it.name == mapName || it.name == "$mapName\$delegate" } ?: return null
        f.isAccessible = true
        var map = f.get(null)
        if (map is Lazy<*>) map = map.value
        val cfg = (map as? Map<*, *>)?.get(id) ?: return null
        val build = field(cfg, "build") ?: field(cfg, "frames") ?: return listOf(cfg)
        return when (build) {
            is Function<*> -> listOf(cfg) + ((runCatching { invokeAny(build) }.getOrNull() as? List<*>) ?: emptyList())
            is List<*> -> listOf(cfg) + build
            else -> listOf(cfg)
        }
    }

    /**
     * Int-state labs (`MtState`, `SsState`, `CsState`, `DsState`, `PpState`): every tab × first
     * parameter × flag, then each remaining field on its own. States a lab rejects are skipped.
     */
    private fun stateSweep(stateClass: String, lab: Any): List<Any?> {
        val fn = (field(lab, "frame") ?: field(lab, "frames")) as? Function<*> ?: return labFrames(lab)
        val k = Class.forName("com.algora.app.feature.topics.$stateClass")
        val ctor = k.declaredConstructors.first { c -> c.parameterTypes.isNotEmpty() && c.parameterTypes.all { it == Int::class.javaPrimitiveType } }
        ctor.isAccessible = true
        val names = k.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }
        val n = ctor.parameterCount
        val flagAt = names.indexOf("flag")
        val tabAt = names.indexOf("tab")
        val paramAt = names.indices.firstOrNull { it != flagAt && it != tabAt && names[it] != "index" }
        val start = IntArray(n).also { a ->
            (field(lab, "initial"))?.let { init -> names.forEachIndexed { i, nm -> (field(init, nm) as? Int)?.let { a[i] = it } } }
            if (tabAt >= 0) (field(lab, "startTab") as? Int)?.let { a[tabAt] = it }
            if (flagAt >= 0) (field(lab, "startFlag") as? Int)?.let { a[flagAt] = it }
        }
        val states = LinkedHashSet<List<Int>>()
        for (t in if (tabAt >= 0) 0..7 else 0..0) for (v in 0..24) for (fl in 0..1) {
            states += start.copyOf().also { a ->
                if (tabAt >= 0) a[tabAt] = t
                if (paramAt != null) a[paramAt] = v
                if (flagAt >= 0) a[flagAt] = fl
            }.toList()
        }
        names.indices.filter { it != tabAt && it != paramAt && it != flagAt }.forEach { i ->
            for (v in 0..9) states += start.copyOf().also { it[i] = v }.toList()
        }
        return listOf<Any?>(lab) + states.mapNotNull { st ->
            runCatching { invokeAny(fn, ctor.newInstance(*st.toTypedArray())) }.getOrNull()
        }
    }

    private fun rsLab(lab: Any): List<Any?> {
        val tabs = (field(lab, "tabs") as? List<*>)?.size?.coerceAtLeast(1) ?: 1
        val fn = field(lab, "frames") as Function<*>
        return listOf(lab) + (0 until tabs).flatMap { t ->
            (0..24).flatMap { p -> (0..1).flatMap { fl -> (runCatching { invokeAny(fn, p, t, fl) as List<*> }.getOrNull() ?: emptyList()) } }
        }
    }

    /**
     * State-driven labs (`EvLab`, regression stories): every value of every parameter with the others
     * at their start, as their own frame probes enumerate them, so slider-only numbers are seen too.
     */
    private fun settings(initial: List<Int>, params: List<*>): List<List<Int>> =
        listOf(initial) + params.flatMapIndexed { p, param ->
            val n = (field(param!!, "values") as? List<*>)?.size ?: 0
            (0 until n).map { v -> initial.toMutableList().also { it[p] = v } }
        }

    private fun evalStates(lab: Any): List<Any?> {
        val initial = field(lab, "initial")!!
        val params = field(lab, "params") as List<*>
        val frame = field(lab, "frame") as Function<*>
        val action = field(lab, "action") as? Function<*>
        val ctor = initial.javaClass.declaredConstructors.first { it.parameterCount == 3 }
        ctor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val idx0 = field(initial, "idx") as List<Int>
        val out = mutableListOf<Any?>(lab)
        settings(idx0, params).forEach { idx ->
            for (sel in 0 until params.size.coerceAtLeast(1)) for (flag in 0..1) {
                val st = ctor.newInstance(idx, sel, flag)
                out += runCatching { invokeAny(frame, st) }.getOrNull()
                action?.let { out += runCatching { invokeAny(it, st) }.getOrNull() }
            }
        }
        return out
    }

    private fun regressionStates(id: String): List<Any?>? {
        val k = Class.forName("com.algora.app.feature.topics.RegressionStoryLabsKt")
        val f = k.declaredFields.firstOrNull { it.name == "regressionStories" || it.name == "regressionStories\$delegate" } ?: return null
        f.isAccessible = true
        var map = f.get(null)
        if (map is Lazy<*>) map = map.value
        val maker = (map as Map<*, *>)[id] ?: return null
        val config = (if (maker is Function<*>) invokeAny(maker) else maker)!!
        @Suppress("UNCHECKED_CAST")
        val initial = field(config, "initial") as List<Int>
        val params = field(config, "params") as List<*>
        val evaluate = field(config, "evaluate") as? Function<*>
        val method = if (evaluate == null) config.javaClass.methods.firstOrNull { it.name == "evaluate" && it.parameterCount == 1 } else null
        return listOf<Any?>(config) + settings(initial, params).map { idx ->
            runCatching { evaluate?.let { invokeAny(it, idx) } ?: method?.invoke(config, idx) }.getOrNull()
        }
    }

    private fun strings(id: String): List<String> {
        val lab: List<Any?> = when (id) {
            in textStoryTopicIds -> labFrames(priv("TextLabsKt", "textTabs", id)!!)
            in regressionStoryTopicIds -> regressionStates(id) ?: emptyList()
            in rlBoardTopicIds -> labFrames(rlBoardLab(id))
            in deepStoryTopicIds -> labFrames(deepLab(id))
            in llmStoryTopicIds -> labFrames(llmLab(id))
            in nlpStoryTopicIds -> nlpLab(id)
            in evalStoryTopicIds -> evalStates(priv("EvalStoryLabsKt", "evalLab", id)!!)
            in metricStoryTopicIds -> stateSweep("MtState", priv("MetricStoryLabsKt", "metricLab", id)!!)
            in ensembleStoryTopicIds -> labFrames(priv("EnsembleStoryLabsKt", "ensembleLab", id)!!)
            in bayesStoryTopicIds -> labFrames(priv("BayesStoryLabsKt", "bayesLab", id)!!)
            in rlStoryTopicIds -> rsLab(priv("RlStoryLabsKt", "rlLab", id)!!)
            in clusterStoryTopicIds -> stateSweep("CsState", priv("ClusterStoryLabsKt", "clusterLab", id)!!)
            in dimStoryTopicIds -> stateSweep("DsState", priv("DimStoryLabsKt", "dimLab", id)!!)
            in mlStoryTopicIds -> labFrames(priv("MlStoryLabsKt", "mlStoryTabs", id)!!)
            in neuralStoryTopicIds -> labFrames(priv("NeuralStoryLabsKt", "neuralStoryTabs", id, 0)!!)
            in preprocessStoryTopicIds -> stateSweep("PpState", priv("PreprocessStoryLabsKt", "preprocessLab", id)!!)
            in seriesStoryTopicIds -> stateSweep("SsState", priv("SeriesStoryLabsKt", "seriesLab", id)!!)
            else -> configFrames("PolicyGradientSectionKt", "pgConfigs", id)
                ?: configFrames("NeuralNetSectionKt", "netConfigs", id)
                ?: configFrames("RlTrainingSectionKt", "rlConfigs", id)
                ?: tokenizerLabConfigs[id]?.let { listOf(it) }
                ?: configFrames("TokenStripSectionKt", "stripConfigs", id)
                ?: configFrames("PointCloudSectionKt", "cloudConfigs", id)
                ?: return emptyList()
        }
        val out = mutableListOf<String>()
        lab.forEach { collect(it, out, 0, mutableSetOf()) }
        return out
    }
}
