package com.mazeppa.secureshare.common.base

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import com.mazeppa.secureshare.common.extension.configureSystemBars
import com.mazeppa.secureshare.common.extension.isLightMode
import com.mazeppa.secureshare.databinding.LayoutSnackbarBinding

abstract class BaseActivity<Binding : ViewBinding> : AppCompatActivity() {

    //    lateinit var connectionObserver: ConnectionObserver
    private var _binding: Binding? = null
    val binding: Binding get() = checkNotNull(_binding) { "Binding is null" }
    abstract val onInflate: (LayoutInflater) -> Binding
//    val viewSize: WidthHeight get() = binding.root.width to binding.root.height

    //Decides whether [BaseActivity] should configure system bars itself or not
    private val configureSystemBars: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("Current activity ${this::class.java.simpleName}")

        //TODO: Uncomment this to enable dark mode
        /*AppCompatDelegate.setDefaultNightMode(
            when (PreferenceProperties.uiMode) {
                UIMode.Dark -> {
                    AppCompatDelegate.MODE_NIGHT_YES
                }

                UIMode.Light -> {
                    AppCompatDelegate.MODE_NIGHT_NO
                }

                else -> {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            }
        )*/
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        _binding = onInflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            initViews()
            setListeners()
            observeValues()
//            connectionObserver()
        }

        if (configureSystemBars) {
            val isLight = isLightMode()

            configureSystemBars(
                root = binding.root,
                lightSystemBar = isLight,
            )
        }
    }

//    private fun connectionObserver() {
//        connectionObserver = NetworkConnectivityObserver(this@BaseActivity)
//        val composeViewConnection =
//            binding.root.findViewById<ComposeView>(R.id.composeViewConnection)
//
//        composeViewConnection?.setContent {
//            val status by connectionObserver.observe()
//                .collectAsState(
//                    initial = (if (isInternetAvailable()) ConnectionObserver.Status.Available else ConnectionObserver.Status.Unavailable)
//                )
//
//            ConnectionNotifier(status)
//        }
//    }

    private fun errorObserver() {
//        val composeViewError =
//            binding.root.findViewById<ComposeView>(R.id.composeViewError)
//        composeViewError?.setContent {
//            val snackbarData by data.collectAsState(initial = SnackbarData(""))
//
//            if (snackbarData.message.isNotEmpty()) {
//                Snackbar(data = snackbarData)
//            }
//        }
    }

    /**
     * Handles restoration of language
     */
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        val config = Configuration()
        applyOverrideConfiguration(config)
    }

    override fun applyOverrideConfiguration(newConfig: Configuration) {
        super.applyOverrideConfiguration(updateConfigurationIfSupported(newConfig))
    }

    open fun updateConfigurationIfSupported(config: Configuration): Configuration? {
//        if (!config.locales.isEmpty) {
//            return config
//        }
//        val languageCode = PreferenceProperties.language.lang
//        val locale = Locale(languageCode)
//        config.setLocale(locale)
//        Locale.setDefault(locale)
        return config
    }

    fun showError(errorMessage: String) {
        Snackbar.make(binding.root, "", Snackbar.LENGTH_LONG).apply {
            val snackbarBinding = LayoutSnackbarBinding.inflate(layoutInflater, null, false)
            view.setBackgroundColor(Color.TRANSPARENT)

            snackbarBinding.textViewErrorMessage.text = errorMessage
            val snackbarLayout = view as Snackbar.SnackbarLayout
            snackbarLayout.setPadding(0, 0, 0, 0)
            snackbarLayout.addView(snackbarBinding.root, 0)
            show()
        }
    }

    open fun Binding.initViews() {}

    open fun Binding.setListeners() {}

    open fun Binding.observeValues() {}

}