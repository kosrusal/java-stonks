
package com.example.myapplication.ishimoku

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.*
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider
import ru.tinkoff.piapi.contract.v1.CandleInterval
import ru.tinkoff.piapi.contract.v1.HistoricCandle
import ru.tinkoff.piapi.contract.v1.MoneyValue
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.core.InvestApi
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var chartFrame: FrameLayout
    private lateinit var tableFrame: FrameLayout
    private lateinit var companySpinner: Spinner
    private lateinit var intervalSpinner: Spinner
    private lateinit var customFigiEditText: EditText
    private lateinit var loadButton: Button
    private lateinit var showIchimokuCheckbox: CheckBox
    private lateinit var progressBar: ProgressBar
    private lateinit var chart: CombinedChart

    private var currentFIGI = "BBG004730N88"
    private var currentTicker = "SBER"
    private val TOKEN = "t.M8UL2mwx2_fLXWFZMf08PKGdiIjDqeM_ZU8ZeoYpbm2ruugFzJYp-6evwQyaQx_-mm5W0qCZxuejtTGZ7CT60g"
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    private lateinit var database: FirebaseDatabase
    private lateinit var candlesRef: DatabaseReference
    private var currentInterval = CandleInterval.CANDLE_INTERVAL_HOUR
    private var api: InvestApi? = null

    private val companiesMap = mapOf(
        "Сбербанк" to Pair("BBG0047315Y7", "SBER"),
        "Газпром" to Pair("BBG004730RP0", "GAZP"),
        "Яндекс" to Pair("TCS00A107T19", "YNDX"),
        "Лукойл" to Pair("BBG004731032", "LKOH"),
        "Роснефть" to Pair("BBG004731354", "ROSN"),
        "МТС" to Pair("BBG004S681W1", "MTSS"),
        "Магнит" to Pair("BBG004RVFCY3", "MGNT")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Ошибка")
                    .setMessage("${e.javaClass.simpleName}: ${e.localizedMessage}")
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            }
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Настройка обработчика неотловленных исключений
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            runOnUiThread {
                Toast.makeText(this, "Произошла ошибка: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                Log.e("APP_CRASH", "Крах приложения", e)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
                System.exit(1)
            }, 2000)
        }

        try {
            initFirebase()
            initViews()
            setupSpinners()
            setupButtonListener()
            initChart()
        } catch (e: Exception) {
            showErrorAndRestart("Ошибка инициализации: ${e.localizedMessage}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            api?.let {
            }
        } catch (e: Exception) {
            Log.e("API_SHUTDOWN", "Ошибка при закрытии API", e)
        }
    }

    private fun showErrorAndRestart(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({
            recreate()
        }, 3000)
    }

    private fun initFirebase() {
        try {
            database = FirebaseDatabase.getInstance("https://ishimoku-db37a-default-rtdb.asia-southeast1.firebasedatabase.app/")
            candlesRef = database.getReference("FIGIs")
        } catch (e: Exception) {
            Log.e("FirebaseInit", "Ошибка инициализации Firebase", e)
            throw e
        }
    }

    private fun initViews() {
        try {
            chartFrame = findViewById(R.id.chartFrame)
            tableFrame = findViewById(R.id.tableFrame)
            companySpinner = findViewById(R.id.companySpinner)
            intervalSpinner = findViewById(R.id.intervalSpinner)
            customFigiEditText = findViewById(R.id.customFigiEditText)
            loadButton = findViewById(R.id.loadButton)
            showIchimokuCheckbox = findViewById(R.id.showIchimokuCheckbox)
            progressBar = findViewById(R.id.progressBar)
        } catch (e: Exception) {
            Log.e("ViewInit", "Ошибка инициализации view", e)
            throw e
        }
    }

    private fun initChart() {
        try {
            chart = CombinedChart(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(0, 0, 0, 0) // Убираем отступы
                }

                setBackgroundColor(Color.WHITE)
                setTouchEnabled(true)
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setDrawBorders(false)

                // Настройка описания
                description = Description().apply {
                    text = "График"
                    textSize = 12f
                    textColor = Color.BLACK
                    setPosition(0f, 0f) // Позиция в левом верхнем углу
                }

                // Настройка легенды
                legend.isEnabled = true
                legend.textColor = Color.BLACK
                legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
                legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                legend.setDrawInside(false)

                // Убираем лишние отступы
                setExtraOffsets(0f, 0f, 0f, 0f)
            }
            chartFrame.addView(chart)
        } catch (e: Exception) {
            Log.e("ChartInit", "Ошибка инициализации графика", e)
            throw e
        }
    }

    private fun setupSpinners() {
        try {
            // Настройка спиннера компаний
            companySpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                companiesMap.keys.toList() + "Другая компания"
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            // Настройка спиннера интервалов
            intervalSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                listOf("Час", "4 часа", "День", "Неделя", "Месяц")
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            // Обработчик выбора компании
            companySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    try {
                        val selected = parent?.getItemAtPosition(position).toString()
                        customFigiEditText.visibility = if (selected == "Другая компания") View.VISIBLE else View.GONE
                    } catch (e: Exception) {
                        Log.e("SpinnerError", "Ошибка выбора компании", e)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Обработчик изменения состояния чекбокса Ишимоку
            showIchimokuCheckbox.setOnCheckedChangeListener { _, isChecked ->
                try {
                    if (::chart.isInitialized && chart.data != null) {
                        // Получаем текущие свечи из графика
                        val candleDataSet = chart.data.getDataSetByIndex(0) as? CandleDataSet ?: return@setOnCheckedChangeListener
                        val candles = mutableListOf<HistoricCandle>()

                        for (i in 0 until candleDataSet.entryCount) {
                            val entry = candleDataSet.getEntryForIndex(i) as? CandleEntry ?: continue
                            candles.add(
                                HistoricCandle.newBuilder()
                                    .setOpen(createQuotation(entry.open))
                                    .setClose(createQuotation(entry.close))
                                    .setHigh(createQuotation(entry.high))
                                    .setLow(createQuotation(entry.low))
                                    .setTime(com.google.protobuf.Timestamp.newBuilder()
                                        .setSeconds((System.currentTimeMillis() / 1000) - (candleDataSet.entryCount - i) * 3600)
                                        .build())
                                    .build()
                            )
                        }

                        // Перестраиваем график с новым состоянием чекбокса
                        setupChart(candles)
                        chart.invalidate()
                    }
                } catch (e: Exception) {
                    Log.e("IchimokuCheckbox", "Ошибка обновления графика", e)
                }
            }

        } catch (e: Exception) {
            Log.e("SpinnerInit", "Ошибка настройки спиннеров", e)
            throw e
        }
    }

    // Вспомогательная функция для создания Quotation
    private fun createQuotation(value: Float): Quotation {
        return Quotation.newBuilder()
            .setUnits(value.toLong())
            .setNano(((value - value.toLong()) * 1e9).toInt())
            .build()
    }

    // Вспомогательная функция для создания MoneyValue
    private fun createMoneyValue(value: Float): MoneyValue {
        return MoneyValue.newBuilder()
            .setUnits(value.toLong())
            .setNano(((value - value.toLong()) * 1e9).toInt())
            .build()
    }

    private fun setupButtonListener() {
        loadButton.setOnClickListener {
            try {
                if (!isNetworkConnected()) {
                    Toast.makeText(this, "Нет интернет-соединения", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                progressBar.visibility = View.VISIBLE
                loadButton.isEnabled = false

                val selectedCompany = companySpinner.selectedItem.toString()

                currentFIGI = if (selectedCompany == "Другая компания") {
                    currentTicker = "CUSTOM"
                    customFigiEditText.text.toString().trim().also {
                        if (it.isEmpty()) throw IllegalArgumentException("FIGI не может быть пустым")
                    }
                } else {
                    companiesMap[selectedCompany]?.let { pair ->
                        currentTicker = pair.second
                        pair.first
                    } ?: throw IllegalStateException("Неизвестная компания")
                }

                currentInterval = when (intervalSpinner.selectedItem.toString()) {
                    "4 часа" -> CandleInterval.CANDLE_INTERVAL_4_HOUR
                    "День" -> CandleInterval.CANDLE_INTERVAL_DAY
                    "Неделя" -> CandleInterval.CANDLE_INTERVAL_WEEK
                    "Месяц" -> CandleInterval.CANDLE_INTERVAL_MONTH
                    else -> CandleInterval.CANDLE_INTERVAL_HOUR
                }

                loadCandles()
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                loadButton.isEnabled = true
                Toast.makeText(this, "Ошибка: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                Log.e("LoadError", "Ошибка подготовки запроса", e)
            }
        }
    }

    private fun isNetworkConnected(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.activeNetworkInfo?.isConnected == true
        } catch (e: Exception) {
            Log.e("NetworkCheck", "Ошибка проверки сети", e)
            false
        }
    }

    private fun loadCandles() {
        try {
            tableFrame.removeAllViews()
            progressBar.visibility = View.VISIBLE
            loadButton.isEnabled = false

            if (currentFIGI.isBlank()) {
                throw IllegalArgumentException("FIGI не может быть пустым")
            }

            Log.d("API_CHECK", "Используемый токен: ${TOKEN.take(5)}...${TOKEN.takeLast(5)}")

            // Устанавливаем Conscrypt в качестве провайдера SSL
            System.setProperty("io.grpc.netty.shaded.io.netty.handler.ssl.noOpenSsl", "true")
            System.setProperty("io.grpc.netty.shaded.io.netty.handler.ssl.opensslProvider", SslProvider.JDK.toString())

            api = try {
                InvestApi.createSandbox(TOKEN)
            } catch (e: Exception) {
                throw IllegalStateException("Ошибка создания API клиента: ${e.message}")
            }

            val (from, to) = try {
                when (currentInterval) {
                    CandleInterval.CANDLE_INTERVAL_HOUR -> Pair(
                        java.time.Instant.now().minus(java.time.Duration.ofDays(1)),
                        java.time.Instant.now()
                    )
                    CandleInterval.CANDLE_INTERVAL_4_HOUR -> Pair(
                        java.time.Instant.now().minus(java.time.Duration.ofDays(3)),
                        java.time.Instant.now()
                    )
                    CandleInterval.CANDLE_INTERVAL_DAY -> Pair(
                        java.time.Instant.now().minus(java.time.Duration.ofDays(30)),
                        java.time.Instant.now()
                    )
                    CandleInterval.CANDLE_INTERVAL_WEEK -> Pair(
                        java.time.Instant.now().minus(java.time.Duration.ofDays(90)),
                        java.time.Instant.now()
                    )
                    CandleInterval.CANDLE_INTERVAL_MONTH -> Pair(
                        java.time.Instant.now().minus(java.time.Duration.ofDays(180)),
                        java.time.Instant.now()
                    )
                    else -> Pair(
                        java.time.Instant.now().minus(java.time.Duration.ofDays(1)),
                        java.time.Instant.now()
                    )
                }
            } catch (e: Exception) {
                throw IllegalStateException("Ошибка определения временного диапазона: ${e.message}")
            }

            Log.d("API_REQUEST", "Запрос: FIGI=$currentFIGI, интервал=$currentInterval, from=$from, to=$to")

            api?.marketDataService?.getCandles(currentFIGI, from, to, currentInterval)
                ?.thenAccept { candles ->
                    runOnUiThread {
                        try {
                            progressBar.visibility = View.GONE
                            loadButton.isEnabled = true

                            if (candles.isEmpty()) {
                                Toast.makeText(this, "Нет данных для отображения", Toast.LENGTH_SHORT).show()
                                return@runOnUiThread
                            }

                            Log.d("API_RESPONSE", "Получено ${candles.size} свечей")
                            saveCandlesToFirebase(candles)
                            setupChart(candles)
                            tableFrame.addView(createCandlesTable(candles))
                        } catch (e: Exception) {
                            handleError("Ошибка обработки данных: ${e.localizedMessage}", e)
                        }
                    }
                }
                ?.exceptionally { ex ->
                    runOnUiThread {
                        val error = ex.cause ?: ex
                        when {
                            error is io.grpc.StatusRuntimeException -> {
                                val status = error.status
                                val details = when {
                                    status.description?.contains("invalid token") == true -> " (Неверный токен)"
                                    status.description?.contains("rate limit") == true -> " (Превышен лимит запросов)"
                                    else -> ""
                                }
                                handleError("Ошибка API ${status.code}$details: ${error.message}", error)
                            }
                            error is java.util.concurrent.TimeoutException -> {
                                handleError("Превышено время ожидания сервера", error)
                            }
                            else -> {
                                handleError("Неизвестная ошибка сервера: ${error.localizedMessage}", error)
                            }
                        }
                    }
                    null
                }
                ?: run {
                    runOnUiThread {
                        handleError("API не инициализировано", IllegalStateException("API клиент не создан"))
                    }
                }
        } catch (e: Exception) {
            handleError("Ошибка загрузки: ${e.localizedMessage}", e)
        }
    }

    private fun handleError(message: String, exception: Throwable) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            loadButton.isEnabled = true

            AlertDialog.Builder(this)
                .setTitle("Ошибка")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()

            Log.e("APP_ERROR", message, exception)
        }
    }

    private fun setupChart(candles: List<HistoricCandle>) {
        try {
            if (candles.isEmpty()) {
                Toast.makeText(this, "Нет данных для графика", Toast.LENGTH_SHORT).show()
                return
            }

            val sortedCandles = candles.sortedBy { it.time.seconds }

            // Очищаем предыдущие данные
            chart.clear()

            // Настройка осей
            setupAxes(sortedCandles)

            // Создаем свечные данные
            val candleData = createCandleData(sortedCandles)

            // Создаем комбинированные данные
            val combinedData = CombinedData().apply {
                setData(candleData)

                // Добавляем данные Ишимоку, если чекбокс активен
                if (showIchimokuCheckbox.isChecked) {
                    setData(createIchimokuData(sortedCandles))
                }
            }

            // Настраиваем график
            chart.apply {
                data = combinedData
                description.text = "$currentTicker (${getIntervalName()}) ${if (showIchimokuCheckbox.isChecked) "с Ишимоку" else ""}"

                // Оптимизация производительности
                setDrawMarkers(false)
                setHardwareAccelerationEnabled(true)

                // Настройка видимой области
                setVisibleXRangeMaximum(30f)
                moveViewToX(combinedData.xMax)

                // Порядок отрисовки
                setDrawOrder(
                    arrayOf(
                        CombinedChart.DrawOrder.LINE,  // Сначала линии Ишимоку
                        CombinedChart.DrawOrder.CANDLE // Затем свечи
                    )
                )

                // Убедимся, что график использует всю ширину
                setExtraOffsets(0f, 0f, 0f, 0f)
                invalidate()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка построения графика: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            Log.e("ChartError", "Ошибка настройки графика", e)
        }
    }

    private fun setupAxes(candles: List<HistoricCandle>) {
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            gridColor = Color.LTGRAY
            textColor = Color.BLACK
            granularity = 1f // Устанавливаем минимальный интервал между значениями
            setAvoidFirstLastClipping(true) // Чтобы не обрезались крайние значения
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val index = value.toInt().coerceIn(0, candles.size - 1)
                    return dateFormat.format(Date(candles[index].time.seconds * 1000))
                }
            }
        }

        chart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = Color.LTGRAY
            textColor = Color.BLACK
            granularity = 1f
            axisMinimum = getMinPrice(candles) * 0.995f
            axisMaximum = getMaxPrice(candles) * 1.005f
        }

        chart.axisRight.isEnabled = false
    }

    private fun createCandleData(candles: List<HistoricCandle>): CandleData {
        val entries = candles.mapIndexed { index, candle ->
            CandleEntry(
                index.toFloat(),
                candle.high.units + candle.high.nano / 1e9f,
                candle.low.units + candle.low.nano / 1e9f,
                candle.open.units + candle.open.nano / 1e9f,
                candle.close.units + candle.close.nano / 1e9f
            )
        }

        val dataSet = CandleDataSet(entries, "Свечи").apply {
            color = Color.rgb(80, 80, 80)
            shadowColor = Color.DKGRAY
            shadowWidth = 0.8f
            decreasingColor = Color.RED
            decreasingPaintStyle = android.graphics.Paint.Style.FILL
            increasingColor = Color.GREEN
            increasingPaintStyle = android.graphics.Paint.Style.FILL
            neutralColor = Color.BLUE
            setDrawValues(false)
        }

        return CandleData(dataSet)
    }

    private fun createIchimokuData(candles: List<HistoricCandle>): LineData {
        val (tenkanSen, kijunSen, senkouSpanA, senkouSpanB, chikouSpan) = calculateIchimoku(candles)
        val lineData = LineData()

        // 1. Create Kumo cloud (fill between SpanA and SpanB)
        val kumoEntries = mutableListOf<Entry>()
        senkouSpanA.forEachIndexed { index, entryA ->
            val entryB = senkouSpanB.getOrNull(index)
            if (entryB != null && !entryA.y.isNaN() && !entryB.y.isNaN()) {
                kumoEntries.add(Entry(entryA.x, entryA.y, entryB.y))
            }
        }

        val kumoDataSet = LineDataSet(kumoEntries, "Kumo").apply {
            color = Color.TRANSPARENT
            setDrawFilled(true)
            fillColor = if (senkouSpanA.last().y > senkouSpanB.last().y) Color.GREEN else Color.RED
            fillAlpha = 80
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }
        lineData.addDataSet(kumoDataSet)

        // 2. Add Ichimoku lines
        listOf(
            Triple(tenkanSen, "Tenkan-sen (9)", Color.RED),
            Triple(kijunSen, "Kijun-sen (26)", Color.BLUE),
            Triple(chikouSpan, "Chikou Span (26)", Color.MAGENTA)
        ).forEach { (entries, label, color) ->
            LineDataSet(entries, label).apply {
                this.color = color
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                if (label.contains("Chikou")) {
                    enableDashedLine(10f, 10f, 0f)
                }
            }.let { lineData.addDataSet(it) }
        }

        return lineData
    }

    private fun createKumoFill(senkouSpanA: List<Entry>, senkouSpanB: List<Entry>): LineDataSet {
        val entries = mutableListOf<Entry>()

        // Создаём точки для заполнения между двумя линиями
        senkouSpanA.forEachIndexed { index, entryA ->
            val entryB = senkouSpanB.getOrNull(index)
            if (entryB != null) {
                entries.add(Entry(entryA.x, entryA.y, entryB.y))
            }
        }

        return LineDataSet(entries, "Kumo").apply {
            color = Color.TRANSPARENT
            setDrawFilled(true)
            fillColor = if (senkouSpanA.last().y > senkouSpanB.last().y) Color.GREEN else Color.RED
            fillAlpha = 80
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }
    }

    private fun calculateIchimoku(candles: List<HistoricCandle>): IchimokuData {
        val result = IchimokuData(
            tenkanSen = calculateConversionLine(candles, 9),
            kijunSen = calculateBaseLine(candles, 26),
            senkouSpanA = calculateLeadingSpanA(candles, 9, 26),
            senkouSpanB = calculateLeadingSpanB(candles, 52),
            chikouSpan = calculateLaggingSpan(candles, 26)
        )

        Log.d("ICHIMOKU", "Tenkan-sen: ${result.tenkanSen.lastOrNull()?.y}")
        Log.d("ICHIMOKU", "Kijun-sen: ${result.kijunSen.lastOrNull()?.y}")
        Log.d("ICHIMOKU", "SpanA: ${result.senkouSpanA.lastOrNull()?.y}")
        Log.d("ICHIMOKU", "SpanB: ${result.senkouSpanB.lastOrNull()?.y}")

        return result
    }

    private fun calculateConversionLine(candles: List<HistoricCandle>, period: Int): List<Entry> {
        return candles.mapIndexed { index, _ ->
            if (index >= period - 1) {
                val subList = candles.subList(max(0, index - period + 1), index + 1)
                val high = subList.maxOf { it.high.units + it.high.nano / 1e9f }
                val low = subList.minOf { it.low.units + it.low.nano / 1e9f }
                Entry(index.toFloat(), (high + low) / 2)
            } else {
                Entry(index.toFloat(), Float.NaN)
            }
        }
    }

    private fun calculateBaseLine(candles: List<HistoricCandle>, period: Int): List<Entry> {
        return calculateConversionLine(candles, period)
    }

    private fun calculateLeadingSpanA(
        candles: List<HistoricCandle>,
        tenkanPeriod: Int,
        kijunPeriod: Int
    ): List<Entry> {
        val tenkan = calculateConversionLine(candles, tenkanPeriod)
        val kijun = calculateBaseLine(candles, kijunPeriod)

        return tenkan.mapIndexed { index, tenkanEntry ->
            if (index >= kijunPeriod - 1) {
                Entry(index.toFloat(), (tenkanEntry.y + kijun[index].y) / 2)
            } else {
                Entry(index.toFloat(), Float.NaN)
            }
        }
    }

    private fun calculateLeadingSpanB(candles: List<HistoricCandle>, period: Int): List<Entry> {
        return calculateConversionLine(candles, period)
    }

    private fun calculateLaggingSpan(candles: List<HistoricCandle>, shift: Int): List<Entry> {
        return candles.mapIndexed { index, _ ->
            if (index + shift < candles.size) {
                Entry(index.toFloat(), candles[index + shift].close.units + candles[index + shift].close.nano / 1e9f)
            } else {
                Entry(index.toFloat(), Float.NaN)
            }
        }
    }

    private fun createCandlesTable(candles: List<HistoricCandle>): ScrollView {
        val scrollView = ScrollView(this)
        val table = TableLayout(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            isStretchAllColumns = true
        }

        val header = TableRow(this).apply {
            addView(TextView(this@MainActivity).apply {
                text = "Дата"
                setTypeface(null, Typeface.BOLD)
                setPadding(8, 8, 8, 8)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Открытие"
                setTypeface(null, Typeface.BOLD)
                setPadding(8, 8, 8, 8)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Закрытие"
                setTypeface(null, Typeface.BOLD)
                setPadding(8, 8, 8, 8)
            })
        }
        table.addView(header)

        candles.takeLast(20).forEach { candle ->
            TableRow(this).apply {
                addView(TextView(this@MainActivity).apply {
                    text = dateFormat.format(Date(candle.time.seconds * 1000))
                    setPadding(8, 8, 8, 8)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "%.2f".format(candle.open.units + candle.open.nano / 1e9)
                    setPadding(8, 8, 8, 8)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "%.2f".format(candle.close.units + candle.close.nano / 1e9)
                    setPadding(8, 8, 8, 8)
                    setTextColor(
                        if (candle.close.units > candle.open.units) Color.GREEN
                        else if (candle.close.units < candle.open.units) Color.RED
                        else Color.BLACK
                    )
                })
            }.also { table.addView(it) }
        }

        scrollView.addView(table)
        return scrollView
    }

    private fun saveCandlesToFirebase(candles: List<HistoricCandle>) {
        val intervalPath = when (currentInterval) {
            CandleInterval.CANDLE_INTERVAL_HOUR -> "Hour"
            CandleInterval.CANDLE_INTERVAL_4_HOUR -> "4Hour"
            CandleInterval.CANDLE_INTERVAL_DAY -> "Day"
            CandleInterval.CANDLE_INTERVAL_WEEK -> "Week"
            CandleInterval.CANDLE_INTERVAL_MONTH -> "Month"
            else -> "Hour"
        }

        candlesRef = database.getReference("FIGIs").child(currentTicker).child(intervalPath)

        candlesRef.removeValue().addOnCompleteListener {
            candles.sortedBy { it.time.seconds }.forEach { candle ->
                val timestamp = candle.time.seconds * 1000
                val dateKey = fullDateFormat.format(Date(timestamp))

                candlesRef.child(dateKey).setValue(mapOf(
                    "open" to (candle.open.units + candle.open.nano / 1e9f),
                    "close" to (candle.close.units + candle.close.nano / 1e9f),
                    "high" to (candle.high.units + candle.high.nano / 1e9f),
                    "low" to (candle.low.units + candle.low.nano / 1e9f),
                    "volume" to candle.volume
                ))
            }
            Toast.makeText(this, "Данные сохранены в Firebase", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            candlesRef.removeValue().addOnFailureListener { e ->
                Log.e("Firebase", "Ошибка сохранения: ${e.message}")
            }
        }
    }

    private fun getMinPrice(candles: List<HistoricCandle>): Float {
        return if (candles.isEmpty()) 0f else candles.minOf { it.low.units + it.low.nano / 1e9f }
    }

    private fun getMaxPrice(candles: List<HistoricCandle>): Float {
        return if (candles.isEmpty()) 0f else candles.maxOf { it.high.units + it.high.nano / 1e9f }
    }

    private fun getIntervalName(): String {
        return when (currentInterval) {
            CandleInterval.CANDLE_INTERVAL_HOUR -> "1 час"
            CandleInterval.CANDLE_INTERVAL_4_HOUR -> "4 часа"
            CandleInterval.CANDLE_INTERVAL_DAY -> "День"
            CandleInterval.CANDLE_INTERVAL_WEEK -> "Неделя"
            CandleInterval.CANDLE_INTERVAL_MONTH -> "Месяц"
            else -> "Неизвестно"
        }
    }

    private data class IchimokuData(
        val tenkanSen: List<Entry>,
        val kijunSen: List<Entry>,
        val senkouSpanA: List<Entry>,
        val senkouSpanB: List<Entry>,
        val chikouSpan: List<Entry>
    )
}
