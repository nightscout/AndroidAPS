package app.aaps.pump.aaruy.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.stringResource
import app.aaps.pump.aaruy.R
import androidx.compose.material3.Text

val buttonHeight: Dp = 48.dp
@Composable
fun MyButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerSize: Dp = 4.dp,
    containerColor: Color = Color(0XFF0095AD),
    contentColor: Color = Color(0xFF007A8D),
    disabledContainerColor: Color = Color(0xFF007A8D),
    disableContentColor: Color = Color(0xFF007A8D),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    verticalPaddingValue: Dp = 10.dp,
    horizontalPaddingValue: Dp = 0.dp,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    var interactionSourceSet = remember { MutableInteractionSource() }
    if (interactionSource != null) {
        interactionSourceSet = interactionSource
    }
    val isPressed by interactionSourceSet.collectIsPressedAsState()

    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(1.dp, 1.dp),
        enabled = enabled,
        shape = RoundedCornerShape(cornerSize),
        colors = ButtonColors(
            containerColor = if (isPressed) disableContentColor else containerColor,
            contentColor = contentColor,
            disabledContentColor = disableContentColor,
            disabledContainerColor = disabledContainerColor
        ),
        elevation = elevation,
        border = border,
        contentPadding = PaddingValues(
            horizontal = horizontalPaddingValue,
            vertical = verticalPaddingValue
        ),
        interactionSource = interactionSourceSet,
        content = content
    )
}

@Composable
fun MyTwoButtonNew(
    modifier1: Modifier = Modifier,
    modifier2: Modifier = Modifier,
    onClick1: () -> Unit,
    onclick2: () -> Unit,
    TextId1: Int = R.string.actions_reservoir_prime,
    TextId2: Int = R.string.setting_next,
    horizontalPaddingValue: Dp = 16.dp,
    spaceSizeDp: Dp = 16.dp,
    isChange: Boolean = false,
    button1Enable:Boolean = true,
    button2Enable:Boolean = true,
    btn1TextColor: Color = Color.White,
    btn2TextColor: Color = Color.White,
    btn1ColorDisable: Color = Color(0xFF005666),
    btn1ColorEnable: Color = Color(0XFF0095AD),
    btn2ColorDisable: Color = Color(0xFF005666),
    btn2ColorEnable: Color = Color(0XFF0095AD),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPaddingValue),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isChange) {
            MyButton(
                onClick = onClick1,
                enabled = button1Enable,
                modifier = modifier1
                    .height(buttonHeight)
                    .weight(1.0f),
                contentColor = btn1ColorEnable,
                containerColor = btn1ColorEnable,
                disableContentColor = btn1ColorDisable,
                disabledContainerColor = btn1ColorDisable
            ) {
                Text(
                    text = stringResource(id = TextId1),
                    color = btn1TextColor,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        } else {
            MyButton(
                onClick = onclick2,
                enabled = button2Enable,
                modifier = modifier2
                    .height(buttonHeight)
                    .weight(1.0f),
                contentColor = Color(0XFF505656),
                containerColor = Color(0XFF505656),
                disableContentColor = Color(0XFF505656),
                disabledContainerColor = Color(0XFF505656)
            ) {
                Text(
                    text = stringResource(id = TextId2),
                    color = btn2TextColor,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        Spacer(modifier = Modifier.width(spaceSizeDp))

        if (isChange) {
            MyButton(
                onClick = onClick1,
                enabled = button1Enable,
                modifier = modifier1
                    .height(buttonHeight)
                    .weight(1.0f)
            ) {
                Text(
                    text = stringResource(id = TextId1),
                    color = btn1TextColor,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        } else {
            MyButton(
                onClick = onclick2,
                enabled = button2Enable,
                modifier = modifier2
                    .height(buttonHeight)
                    .weight(1.0f),
                contentColor = btn2ColorEnable,
                containerColor = btn2ColorEnable,
                disableContentColor = btn2ColorDisable,
                disabledContainerColor = btn2ColorDisable
            ) {
                Text(
                    text = stringResource(id = TextId2),
                    color = btn2TextColor,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}