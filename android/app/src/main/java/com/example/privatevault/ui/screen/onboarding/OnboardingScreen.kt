package com.example.privatevault.ui.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.privatevault.app.OnboardingPage
import com.example.privatevault.R

@Composable
fun OnboardingScreen(
    page: OnboardingPage,
    permissionGranted: Boolean?,
    onRequestStorage: () -> Unit,
    onStartChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = when (page) {
        OnboardingPage.Permission -> OnboardingCopy(
            title = stringResource(R.string.onboarding_permission_title),
            body = stringResource(R.string.onboarding_permission_body),
            cta = stringResource(R.string.onboarding_permission_cta)
        )
        OnboardingPage.Result -> if (permissionGranted == true) {
            OnboardingCopy(
                title = stringResource(R.string.onboarding_enabled_title),
                body = stringResource(R.string.onboarding_enabled_body),
                cta = stringResource(R.string.start_chat)
            )
        } else {
            OnboardingCopy(
                title = stringResource(R.string.onboarding_disabled_title),
                body = stringResource(R.string.onboarding_disabled_body),
                cta = stringResource(R.string.start_chat)
            )
        }
    }

    Scaffold(modifier = modifier) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = copy.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = copy.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = when (page) {
                            OnboardingPage.Permission -> onRequestStorage
                            OnboardingPage.Result -> onStartChat
                        }
                    ) {
                        Text(copy.cta)
                    }
                }
            }
        }
    }
}

private data class OnboardingCopy(
    val title: String,
    val body: String,
    val cta: String
)
