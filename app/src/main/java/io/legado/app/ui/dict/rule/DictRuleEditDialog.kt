package io.legado.app.ui.dict.rule

import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.dict.DictDialog
import io.legado.app.ui.widget.compose.AppDialogFrame
import io.legado.app.ui.widget.compose.AppDialogSwitchRow
import io.legado.app.ui.widget.compose.AppRuleFieldSpacer
import io.legado.app.ui.widget.compose.AppRuleTextField
import io.legado.app.ui.widget.compose.ComposeConfirmDialog
import io.legado.app.ui.widget.compose.ComposeDialogFragment
import io.legado.app.ui.widget.compose.AppDialogSize
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.utils.*
import io.legado.app.ui.widget.compose.showComposeTextInputDialog
import io.legado.app.utils.showDialogFragment

class DictRuleEditDialog() : ComposeDialogFragment() {

    val viewModel by viewModels<DictRuleEditViewModel>()
    override val dialogSize: AppDialogSize = AppDialogSize.Management

    enum class EditField {
        Name, UrlRule, ShowRule
    }

    private var nameValue by mutableStateOf(TextFieldValue(""))
    private var urlRuleValue by mutableStateOf(TextFieldValue(""))
    private var showRuleValue by mutableStateOf(TextFieldValue(""))
    private var htmlModeValue by mutableStateOf(false)
    private var focusedField: EditField? = null
    private var loaded by mutableStateOf(false)
    private var forceDismiss = false

    constructor(name: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): ComposeView {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LaunchedEffect(Unit) {
                    viewModel.initData(arguments?.getString("name")) {
                        upRuleView(viewModel.dictRule)
                        loaded = true
                    }
                }
                if (loaded) {
                    DictRuleEditContent(
                        name = nameValue,
                        onNameChange = { nameValue = it },
                        urlRule = urlRuleValue,
                        onUrlRuleChange = { urlRuleValue = it },
                        showRule = showRuleValue,
                        onShowRuleChange = { showRuleValue = it },
                        htmlMode = htmlModeValue,
                        onHtmlModeChange = { newMode ->
                            // 切换前：当前输入框内容写回旧模式字段
                            viewModel.dictRule?.let { rule ->
                                if (htmlModeValue) rule.htmlShowRule = showRuleValue.text
                                else rule.showRule = showRuleValue.text
                            }
                            htmlModeValue = newMode
                            // 切换后：从新模式字段载入显示规则输入框
                            showRuleValue = TextFieldValue(
                                if (newMode) viewModel.dictRule?.htmlShowRule.orEmpty()
                                else viewModel.dictRule?.showRule.orEmpty()
                            )
                        },
                        onFieldFocused = { focusedField = it },
                        onFullEdit = ::onFullEditClicked,
                        onSave = {
                            viewModel.save(getDictRule()) {
                                forceDismiss = true
                                dismissAllowingStateLoss()
                            }
                        },
                        onCopyRule = { viewModel.copyRule(getDictRule()) },
                        onPasteRule = {
                            viewModel.pasteRule {
                                upRuleView(it)
                            }
                        },
                        onTest = { testRule() },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val field = focusedField
            if (field == null) {
                toastOnUi(R.string.focus_lost_on_textbox)
                return@registerForActivityResult
            }
            result.data?.getStringExtra("text")?.let {
                val cursor = result.data?.getIntExtra("cursorPosition", -1)
                    ?.takeIf { position -> position in 0..it.length }
                    ?: it.length
                setFieldValue(field, TextFieldValue(it, TextRange(cursor)))
            }
        }
    }

    private fun onFullEditClicked() {
        val field = focusedField
        if (field != null) {
            val value = getFieldValue(field)
            val intent = Intent(requireActivity(), CodeEditActivity::class.java).apply {
                putExtra("text", value.text)
                putExtra("title", fieldTitle(field))
                putExtra("cursorPosition", value.selection.start)
            }
            textEditLauncher.launch(intent)
        } else {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }

    private fun testRule() {
        showComposeTextInputDialog(
            title = getString(R.string.test_dict_rule),
            hint = getString(R.string.test_word),
            initialValue = getString(R.string.test_dict_rule_default),
            positiveText = getString(R.string.ok),
            negativeText = getString(R.string.cancel),
            onPositive = { word ->
                if (word.isNotBlank()) {
                    val dictRule = getDictRule()
                    if (dictRule.name.isNotBlank()) {
                        viewModel.save(dictRule) {
                            showDialogFragment(DictDialog(word.trim()))
                        }
                    } else {
                        toastOnUi(R.string.name)
                    }
                }
            }
        )
    }

    private fun getFieldValue(field: EditField): TextFieldValue {
        return when (field) {
            EditField.Name -> nameValue
            EditField.UrlRule -> urlRuleValue
            EditField.ShowRule -> showRuleValue
        }
    }

    private fun setFieldValue(field: EditField, value: TextFieldValue) {
        when (field) {
            EditField.Name -> nameValue = value
            EditField.UrlRule -> urlRuleValue = value
            EditField.ShowRule -> showRuleValue = value
        }
    }

    private fun fieldTitle(field: EditField): String {
        return when (field) {
            EditField.Name -> getString(R.string.name)
            EditField.UrlRule -> getString(R.string.url_rule)
            EditField.ShowRule -> getString(R.string.show_rule)
        }
    }

    private fun upRuleView(dictRule: DictRule?) {
        nameValue = TextFieldValue(dictRule?.name.orEmpty())
        urlRuleValue = TextFieldValue(dictRule?.urlRule.orEmpty())
        htmlModeValue = dictRule?.htmlMode ?: false
        // HTML 模式显示规则单独存储：按当前模式从对应字段载入
        showRuleValue = TextFieldValue(
            if (htmlModeValue) dictRule?.htmlShowRule.orEmpty()
            else dictRule?.showRule.orEmpty()
        )
    }

    private fun getDictRule(): DictRule {
        val dictRule = viewModel.dictRule?.copy() ?: DictRule()
        dictRule.name = nameValue.text
        dictRule.urlRule = urlRuleValue.text
        dictRule.htmlMode = htmlModeValue
        // HTML 模式显示规则单独存储：按当前模式写回对应字段
        if (htmlModeValue) {
            dictRule.htmlShowRule = showRuleValue.text
        } else {
            dictRule.showRule = showRuleValue.text
        }
        return dictRule
    }

    private fun isSame(): Boolean{
        val dictRule = viewModel.dictRule ?: return nameValue.text.isEmpty()
        return dictRule.name == nameValue.text &&
            dictRule.urlRule == urlRuleValue.text &&
            dictRule.htmlMode == htmlModeValue &&
            ((htmlModeValue && dictRule.htmlShowRule == showRuleValue.text) ||
                (!htmlModeValue && dictRule.showRule == showRuleValue.text))
    }

    override fun dismiss() {
        if (forceDismiss || isSame()) {
            super.dismiss()
        } else {
            ComposeConfirmDialog.create(
                title = getString(R.string.exit),
                message = getString(R.string.exit_no_save),
                positiveText = getString(R.string.yes),
                negativeText = getString(R.string.no),
                onPositive = {},
                onNegative = {
                    forceDismiss = true
                    dismissWithoutConfirm()
                }
            ).show(childFragmentManager, "exitConfirm")
        }
    }

    private fun dismissWithoutConfirm() {
        forceDismiss = true
        super.dismiss()
    }

    class DictRuleEditViewModel(application: Application) : BaseViewModel(application) {

        var dictRule: DictRule? = null

        fun initData(name: String?, onFinally: () -> Unit) {
            execute {
                if (dictRule == null && name != null) {
                    dictRule = appDb.dictRuleDao.getByName(name)
                }
            }.onFinally {
                onFinally.invoke()
            }
        }

        fun save(newDictRule: DictRule, onFinally: () -> Unit) {
            execute {
                dictRule?.let {
                    appDb.dictRuleDao.delete(it)
                }
                appDb.dictRuleDao.insert(newDictRule)
                dictRule = newDictRule
            }.onFinally {
                onFinally.invoke()
            }
        }

        fun copyRule(dictRule: DictRule) {
            context.sendToClip(GSON.toJson(dictRule))
        }

        fun pasteRule(success: (DictRule) -> Unit) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                context.toastOnUi("剪贴板没有内容")
                return
            }
            execute {
                GSON.fromJsonObject<DictRule>(text).getOrThrow()
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi("格式不对")
            }
        }

    }

}

@Composable
private fun DictRuleEditContent(
    name: TextFieldValue,
    onNameChange: (TextFieldValue) -> Unit,
    urlRule: TextFieldValue,
    onUrlRuleChange: (TextFieldValue) -> Unit,
    showRule: TextFieldValue,
    onShowRuleChange: (TextFieldValue) -> Unit,
    htmlMode: Boolean,
    onHtmlModeChange: (Boolean) -> Unit,
    onFieldFocused: (DictRuleEditDialog.EditField) -> Unit,
    onFullEdit: () -> Unit,
    onSave: () -> Unit,
    onCopyRule: () -> Unit,
    onPasteRule: () -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit
) {
    val style = rememberAppDialogStyle()
    val palette = style.toMiuixPalette()
    AppDialogFrame(
        title = stringResource(R.string.dict_rule),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppRuleTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.name),
                    singleLine = true,
                    style = style,
                    onFocused = { onFieldFocused(DictRuleEditDialog.EditField.Name) }
                )
                AppRuleFieldSpacer()
                AppRuleTextField(
                    value = urlRule,
                    onValueChange = onUrlRuleChange,
                    label = stringResource(R.string.url_rule),
                    singleLine = true,
                    style = style,
                    onFocused = { onFieldFocused(DictRuleEditDialog.EditField.UrlRule) }
                )
                AppRuleFieldSpacer()
                AppRuleTextField(
                    value = showRule,
                    onValueChange = onShowRuleChange,
                    label = if (htmlMode) {
                        stringResource(R.string.show_rule_html_hint)
                    } else {
                        stringResource(R.string.show_rule)
                    },
                    minLines = 5,
                    maxLines = 12,
                    style = style,
                    onFocused = { onFieldFocused(DictRuleEditDialog.EditField.ShowRule) }
                )
                AppRuleFieldSpacer()
                AppDialogSwitchRow(
                    text = stringResource(R.string.dict_rule_html_mode),
                    checked = htmlMode,
                    onCheckedChange = onHtmlModeChange
                )
            }
        },
        actions = {
            LegadoMiuixActionButton(
                text = stringResource(R.string.test_dict_rule),
                palette = palette,
                onClick = onTest,
                cornerRadius = style.actionRadius
            )
            Spacer(modifier = Modifier.width(8.dp))
            LegadoMiuixActionButton(
                text = stringResource(R.string.edit_content),
                palette = palette,
                onClick = onFullEdit,
                cornerRadius = style.actionRadius
            )
            Spacer(modifier = Modifier.width(8.dp))
            LegadoMiuixActionButton(
                text = stringResource(R.string.paste_rule),
                palette = palette,
                onClick = onPasteRule,
                cornerRadius = style.actionRadius
            )
            Spacer(modifier = Modifier.width(8.dp))
            LegadoMiuixActionButton(
                text = stringResource(R.string.copy_rule),
                palette = palette,
                onClick = onCopyRule,
                cornerRadius = style.actionRadius
            )
            Spacer(modifier = Modifier.width(8.dp))
            LegadoMiuixActionButton(
                text = stringResource(R.string.cancel),
                palette = palette,
                onClick = onDismiss,
                cornerRadius = style.actionRadius
            )
            Spacer(modifier = Modifier.width(8.dp))
            LegadoMiuixActionButton(
                text = stringResource(R.string.action_save),
                palette = palette,
                onClick = onSave,
                primary = true,
                cornerRadius = style.actionRadius
            )
        }
    )
}
