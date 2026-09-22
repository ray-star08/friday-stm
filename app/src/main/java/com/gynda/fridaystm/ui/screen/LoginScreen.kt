package com.gynda.fridaystm.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import com.gynda.fridaystm.ui.theme.ComponentSize
import com.gynda.fridaystm.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gynda.fridaystm.R
import com.gynda.fridaystm.ui.component.BigActionButton
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.LoginUiState
import com.gynda.fridaystm.viewmodel.LoginViewModel
import com.gynda.fridaystm.viewmodel.MainNavTarget
import com.gynda.fridaystm.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Login — stateful holder (SKILL.md §4.1). Owns [LoginViewModel], collects state
 * lifecycle-aware, and navigates once sign-in succeeds — role-aware per spec 1.
 *
 * @param onLoginSuccess legacy single-route callback (fallback)
 * @param onLoginSuccessStudent GURU/ADMIN check: SISWA → Home
 * @param onLoginSuccessTeacher GURU/ADMIN → TeacherDashboard
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel,
    mainViewModel: MainViewModel? = null,
    onLoginSuccess: (() -> Unit)? = null,
    onLoginSuccessStudent: (() -> Unit)? = null,
    onLoginSuccessTeacher: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.success) {
        if (!state.success) return@LaunchedEffect
        // Role-aware navigation: use MainViewModel when available, else fallback.
        if (mainViewModel != null && (onLoginSuccessStudent != null || onLoginSuccessTeacher != null)) {
            scope.launch {
                val target = mainViewModel.resolvePostLoginTarget()
                when (target) {
                    MainNavTarget.TeacherDashboard -> (onLoginSuccessTeacher ?: onLoginSuccess)?.invoke()
                    MainNavTarget.StudentHome -> (onLoginSuccessStudent ?: onLoginSuccess)?.invoke()
                    else -> onLoginSuccess?.invoke() ?: onLoginSuccessStudent?.invoke()
                }
            }
        } else {
            onLoginSuccess?.invoke()
            onLoginSuccessStudent?.invoke()
        }
    }

    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

@Deprecated("Use role-aware overload with MainViewModel", ReplaceWith("LoginScreen(modifier, viewModel, mainViewModel, onLoginSuccess)"))
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel,
) {
    LoginScreen(
        modifier = modifier,
        viewModel = viewModel,
        onLoginSuccess = onLoginSuccess,
    )
}

/** Login — stateless content; renders purely from [state] so it is previewable. */
@Composable
fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = ComponentSize.formMaxWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(Spacing.s24),
            verticalArrangement = Arrangement.spacedBy(Spacing.s24),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.size(ComponentSize.brandMark), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.redesign_brand_mark), style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Column {
                    Text(stringResource(R.string.redesign_brand), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.redesign_school), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text(stringResource(R.string.redesign_login_heading), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.redesign_login_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(ComponentSize.border, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(Spacing.s20), verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(R.string.login_email_label)) },
                        singleLine = true,
                        enabled = !state.submitting,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        isError = state.errorResId != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.login_password_label)) },
                        singleLine = true,
                        enabled = !state.submitting,
                        shape = MaterialTheme.shapes.small,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (state.canSubmit && !state.submitting) onSubmit() }),
                        isError = state.errorResId != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.errorResId?.let { error ->
                        Text(stringResource(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    BigActionButton(
                        text = stringResource(R.string.login_submit),
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        loading = state.submitting,
                    )
                }
            }
            Text(
                stringResource(R.string.redesign_login_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginContentPreview() {
    FridaySTMTheme {
        LoginContent(
            state = LoginUiState(email = "budi@smkn1cimahi.sch.id", password = "secret"),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, name = "Login · Error")
@Composable
private fun LoginErrorPreview() {
    FridaySTMTheme {
        LoginContent(
            state = LoginUiState(email = "x", password = "y", errorResId = R.string.login_error_invalid),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}
