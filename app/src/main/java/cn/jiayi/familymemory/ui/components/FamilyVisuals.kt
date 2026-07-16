package cn.jiayi.familymemory.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.jiayi.familymemory.R
import cn.jiayi.familymemory.data.local.PersonEntity

@Composable
fun EmptyState(
    @DrawableRes image: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painterResource(image), contentDescription = null,
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(28.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun StatusBanner(message: String, isError: Boolean, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Card(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(container).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(message, color = content, modifier = Modifier.weight(1f))
            if (isError && onRetry != null) OutlinedButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
fun PersonAvatar(person: PersonEntity, size: Dp = 58.dp, modifier: Modifier = Modifier) {
    val image = when (person.gender?.lowercase()) {
        "male", "man", "男" -> R.drawable.avatar_default_male
        "female", "woman", "女" -> R.drawable.avatar_default_female
        else -> R.drawable.avatar_default_neutral
    }
    Image(
        painterResource(image), contentDescription = "${person.name}的默认头像",
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
