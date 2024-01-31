package com.x8bit.bitwarden.ui.auth.feature.resetpassword

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.ui.platform.base.util.asText
import com.x8bit.bitwarden.ui.platform.components.BasicDialogState
import com.x8bit.bitwarden.ui.platform.components.BitwardenBasicDialog
import com.x8bit.bitwarden.ui.platform.components.BitwardenLoadingDialog
import com.x8bit.bitwarden.ui.platform.components.BitwardenMediumTopAppBar
import com.x8bit.bitwarden.ui.platform.components.BitwardenPasswordField
import com.x8bit.bitwarden.ui.platform.components.BitwardenScaffold
import com.x8bit.bitwarden.ui.platform.components.BitwardenTextButton
import com.x8bit.bitwarden.ui.platform.components.BitwardenTextField
import com.x8bit.bitwarden.ui.platform.components.BitwardenTwoButtonDialog
import com.x8bit.bitwarden.ui.platform.components.LoadingDialogState

/**
 * The top level composable for the Reset Password screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun ResetPasswordScreen(
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    when (val dialog = state.dialogState) {
        is ResetPasswordState.DialogState.Error -> {
            BitwardenBasicDialog(
                visibilityState = BasicDialogState.Shown(
                    title = dialog.title ?: R.string.an_error_has_occurred.asText(),
                    message = dialog.message,
                ),
                onDismissRequest = remember(viewModel) {
                    { viewModel.trySendAction(ResetPasswordAction.DialogDismiss) }
                },
            )
        }

        is ResetPasswordState.DialogState.Loading -> {
            BitwardenLoadingDialog(
                visibilityState = LoadingDialogState.Shown(
                    text = dialog.message,
                ),
            )
        }

        null -> Unit
    }

    var shouldShowLogoutConfirmationDialog by remember { mutableStateOf(false) }
    val onLogoutClicked = remember(viewModel) {
        { viewModel.trySendAction(ResetPasswordAction.ConfirmLogoutClick) }
    }
    if (shouldShowLogoutConfirmationDialog) {
        BitwardenTwoButtonDialog(
            title = stringResource(id = R.string.log_out),
            message = stringResource(id = R.string.logout_confirmation),
            confirmButtonText = stringResource(id = R.string.yes),
            dismissButtonText = stringResource(id = R.string.cancel),
            onConfirmClick = {
                shouldShowLogoutConfirmationDialog = false
                onLogoutClicked()
            },
            onDismissClick = { shouldShowLogoutConfirmationDialog = false },
            onDismissRequest = { shouldShowLogoutConfirmationDialog = false },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    BitwardenScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BitwardenMediumTopAppBar(
                title = stringResource(id = R.string.update_master_password),
                scrollBehavior = scrollBehavior,
                actions = {
                    BitwardenTextButton(
                        label = stringResource(id = R.string.log_out),
                        onClick = { shouldShowLogoutConfirmationDialog = true },
                    )
                    BitwardenTextButton(
                        label = stringResource(id = R.string.submit),
                        onClick = remember(viewModel) {
                            { viewModel.trySendAction(ResetPasswordAction.SubmitClick) }
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        ResetPasswordScreeContent(
            state = state,
            onCurrentPasswordInputChanged = remember(viewModel) {
                { viewModel.trySendAction(ResetPasswordAction.CurrentPasswordInputChanged(it)) }
            },
            onPasswordInputChanged = remember(viewModel) {
                { viewModel.trySendAction(ResetPasswordAction.PasswordInputChanged(it)) }
            },
            onRetypePasswordInputChanged = remember(viewModel) {
                { viewModel.trySendAction(ResetPasswordAction.RetypePasswordInputChanged(it)) }
            },
            onPasswordHintInputChanged = remember(viewModel) {
                { viewModel.trySendAction(ResetPasswordAction.PasswordHintInputChanged(it)) }
            },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Suppress("LongMethod")
private fun ResetPasswordScreeContent(
    state: ResetPasswordState,
    onCurrentPasswordInputChanged: (String) -> Unit,
    onPasswordInputChanged: (String) -> Unit,
    onRetypePasswordInputChanged: (String) -> Unit,
    onPasswordHintInputChanged: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .semantics { testTagsAsResourceId = true }
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {

        Text(
            text = stringResource(id = R.string.update_weak_master_password_warning),
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(16.dp)
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        val passwordPolicyContent = listOf(
            stringResource(id = R.string.master_password_policy_in_effect),
        )
            .plus(state.policies.map { it() })
            .joinToString("\n  •  ")
        Text(
            text = passwordPolicyContent,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(16.dp)
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        BitwardenPasswordField(
            label = stringResource(id = R.string.current_master_password),
            value = state.currentPasswordInput,
            onValueChange = onCurrentPasswordInputChanged,
            modifier = Modifier
                .semantics { testTag = "MasterPasswordField" }
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        BitwardenPasswordField(
            label = stringResource(id = R.string.master_password),
            value = state.passwordInput,
            onValueChange = onPasswordInputChanged,
            modifier = Modifier
                .semantics { testTag = "NewPasswordField" }
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        BitwardenPasswordField(
            label = stringResource(id = R.string.retype_master_password),
            value = state.retypePasswordInput,
            onValueChange = onRetypePasswordInputChanged,
            modifier = Modifier
                .semantics { testTag = "RetypePasswordField" }
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        BitwardenTextField(
            label = stringResource(id = R.string.master_password_hint),
            value = state.passwordHintInput,
            onValueChange = onPasswordHintInputChanged,
            hint = stringResource(id = R.string.master_password_hint_description),
            modifier = Modifier
                .semantics { testTag = "MasterPasswordHintLabel" }
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
