package kz.duken.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.duken.mobile.R
import kz.duken.mobile.ui.components.errorText

@Composable
fun AuthScreen(
    register: Boolean,
    vm: AuthViewModel,
    sessionExpired: Boolean,
    onSwitch: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val email by vm.email.collectAsStateWithLifecycle()
    // Пароль не кладётся в SavedStateHandle: он не должен попадать в сохранённое состояние на диске
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(if (register) R.string.register_title else R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("auth_title"),
        )
        if (sessionExpired) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    stringResource(R.string.session_expired),
                    Modifier.padding(12.dp).testTag("session_expired_banner"),
                )
            }
        }
        OutlinedTextField(
            value = email,
            onValueChange = vm::onEmail,
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            isError = state.emailInvalid,
            supportingText = if (state.emailInvalid) {
                { Text(stringResource(R.string.error_email_format), Modifier.testTag("email_error")) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("email_input"),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; vm.clearErrors() },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            isError = state.passwordTooShort,
            supportingText = if (state.passwordTooShort) {
                { Text(stringResource(R.string.error_password_short), Modifier.testTag("password_error")) }
            } else null,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }, modifier = Modifier.testTag("toggle_password")) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submit(register, password) }),
            modifier = Modifier.fillMaxWidth().testTag("password_input"),
        )
        state.error?.let {
            Text(errorText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("auth_error"))
        }
        Button(
            onClick = { vm.submit(register, password) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag(if (register) "register_button" else "login_button"),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(if (register) R.string.register_button else R.string.login_button))
        }
        TextButton(onClick = onSwitch, modifier = Modifier.fillMaxWidth().testTag("switch_auth_mode")) {
            Text(stringResource(if (register) R.string.to_login else R.string.to_register))
        }
    }
}
