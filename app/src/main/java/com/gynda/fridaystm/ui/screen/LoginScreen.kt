package com.gynda.fridaystm.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.login_email_label)) },
            singleLine = true,
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            isError = state.errorResId != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.login_password_label)) },
            singleLine = true,
            enabled = !state.submitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = state.errorResId != null,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.errorResId != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(state.errorResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))
        BigActionButton(
            text = stringResource(R.string.login_submit),
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.submitting,
        )
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
