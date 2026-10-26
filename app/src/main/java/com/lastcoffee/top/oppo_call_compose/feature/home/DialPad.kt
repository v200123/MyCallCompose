package com.lastcoffee.top.oppo_call_compose.feature.home

import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.SpannableStringBuilder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoDivider
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoPrimary
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoSecondary
import com.lastcoffee.top.oppo_call_compose.ui.common.oppoTextStyle
import com.lastcoffee.top.oppo_call_compose.ui.common.rememberDialpadTone
import com.lastcoffee.top.oppo_call_compose.ui.common.rememberDialpadToneInteractionSource

private data class DialKey(val digit: String, val letters: String, val drawable: Int? = null)

private val dialKeys = listOf(
    DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF"),
    DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
    DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
    DialKey("*", ",", R.drawable.oppo_pb_ic_dial_key_star), DialKey("0", "+"),
    DialKey("#", ";", R.drawable.oppo_pb_ic_dial_key_pound),
)

internal fun formatDialNumber(number: String): String =
    SpannableStringBuilder(number).also {
        PhoneNumberFormattingTextWatcher("CN").afterTextChanged(it)
    }.toString()

@Composable
internal fun OppoDialNumberHeader(number: String, location: String?, showDivider: Boolean = false) {
    // DialEditText uses the platform's as-you-type formatter, with raw input kept
    // separately for matching, deleting and dialing.
    val displayNumber = remember(number) { formatDialNumber(number) }
    // afterTextChanged overrides the XML's 32sp: 30dp up to 12 digits,
    // then progressively reduces it to 28dp at 17 digits.
    val textSizeDp = 30f - (number.length - 12).coerceIn(0, 5) * 0.4f
    val textSize = with(LocalDensity.current) { textSizeDp.dp.toSp() }
    Column(Modifier.fillMaxWidth().height(56.33.dp)) {
        // digits_container.xml: number/location weights are 4 and 2.5; the location
        // keeps its space when unavailable, so an async lookup cannot move the number.
        Box(
            Modifier.fillMaxWidth().weight(4f).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(displayNumber, style = oppoTextStyle(textSize, weight = FontWeight.SemiBold),
                maxLines = 1, overflow = TextOverflow.Clip)
        }
        Box(Modifier.fillMaxWidth().weight(2.5f), contentAlignment = Alignment.TopCenter) {
            Text(location.orEmpty(), style = oppoTextStyle(14.sp, OppoSecondary), maxLines = 1)
        }
        if (showDivider) OppoDivider(start = 16.dp, end = 16.dp)
        else Spacer(Modifier.height(0.33.dp))
    }
}

@Composable
internal fun OppoDialPad(
    number: String,
    onNumberChange: (String) -> Unit,
    onDial: (Int) -> Unit,
    onDismiss: () -> Unit,
    simLabels: List<String> = emptyList(),
    dialpadToneEnabled: Boolean = false,
) {
    val playTone = rememberDialpadTone(dialpadToneEnabled)
    // dialpad_container.xml + DialpadView: 346dp, 4 x 58dp keys, 20dp grid top,
    // 56dp bottom controls and 20dp bottom padding. pb_bg_dialer.9.png supplies the handle.
    Box(Modifier.fillMaxWidth().height(346.dp)) {
        Surface(
            Modifier.fillMaxSize().padding(top = 12.dp),
            color = Color(0xFFFAFAFA),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {}
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                .size(36.dp, 4.dp).clip(CircleShape).background(Color(0xFFC5C5C5)),
        )
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(38.dp))
            Column(Modifier.widthIn(max = 512.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                dialKeys.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(58.dp)) {
                        row.forEach { key ->
                            val interactionSource = rememberDialpadToneInteractionSource(key.digit, playTone)
                            Box(
                                Modifier.weight(1f).height(58.dp).combinedClickable(
                                    interactionSource = interactionSource,
                                    onClickLabel = "输入${key.digit}",
                                    onClick = { onNumberChange(number + key.digit) },
                                    onLongClick = when {
                                        key.digit == "0" -> ({ onNumberChange(number + "+") })
                                        key.digit == "*" && number.isNotEmpty() -> ({ onNumberChange(number + ",") })
                                        key.digit == "#" && number.isNotEmpty() -> ({ onNumberChange(number + ";") })
                                        else -> null
                                    },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                OppoDialKeyContent(key)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.widthIn(max = 512.dp).fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(56.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.oppo_pb_ic_hide_dial), "收起拨号盘", Modifier.size(24.dp))
                }
                if (simLabels.size > 1) {
                    Row(Modifier.width(222.dp).height(40.dp)) {
                        simLabels.take(2).forEachIndexed { index, label ->
                            if (index > 0) Spacer(Modifier.width(10.dp))
                            OppoSimDialButton(index, label, onDial)
                        }
                    }
                } else {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF00BD13)).clickable { onDial(1) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(R.drawable.oppo_pb_ic_dial_call), "拨打", Modifier.size(36.dp))
                        }
                    }
                }
                Box(
                    Modifier.weight(1f).height(56.dp).combinedClickable(
                        onClick = { onNumberChange(number.dropLast(1)) },
                        onLongClick = { onNumberChange("") },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (number.isEmpty()) R.drawable.oppo_pb_dr_dial_delete_disabled else R.drawable.oppo_pb_dr_dial_delete_normal),
                        "删除号码，长按清空", Modifier.size(24.dp), tint = Color.Unspecified,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun OppoDialKeyContent(key: DialKey) {
    // dialpad_button.xml uses DP_28 / DP_10 rather than scaled font units.
    val density = LocalDensity.current
    val numberSize = with(density) { 28.dp.toSp() }
    val letterSize = with(density) { 10.dp.toSp() }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            if (key.drawable != null) {
                Image(painterResource(key.drawable), key.digit)
            } else {
                Text(key.digit, style = oppoTextStyle(numberSize, OppoPrimary, FontWeight.Medium))
            }
            if (key.digit == "1") {
                Image(
                    painterResource(R.drawable.oppo_pb_ic_dial_key_voicemail), "语音信箱",
                    Modifier.padding(top = 4.dp).size(14.dp, 8.dp),
                )
            } else {
                Text(
                    key.letters,
                    modifier = Modifier.padding(top = when (key.digit) {
                        "*" -> 3.dp
                        "#" -> 6.dp
                        else -> 0.dp
                    }),
                    style = oppoTextStyle(letterSize, OppoSecondary),
                )
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val digit = measurables[0].measure(loose)
        val letters = measurables[1].measure(loose)
        // DialpadImageButton.p uses negative margins to bring the secondary
        // label up under the digit; star and pound have their own icon margins.
        val iconTop = if (key.drawable != null) 6.dp.roundToPx() else 0
        val overlap = (if (key.drawable != null) 8.dp else 6.dp).roundToPx()
        val placeholder = if (key.digit == "1") 2.dp.roundToPx() else 0
        val contentHeight = iconTop + digit.height - overlap + letters.height + placeholder
        val top = (constraints.maxHeight - contentHeight) / 2
        layout(constraints.maxWidth, constraints.maxHeight) {
            digit.placeRelative((constraints.maxWidth - digit.width) / 2, top + iconTop)
            letters.placeRelative((constraints.maxWidth - letters.width) / 2, top + iconTop + digit.height - overlap)
        }
    }
}

@Composable
private fun OppoSimDialButton(index: Int, label: String, onDial: (Int) -> Unit) {
    val textSize = with(LocalDensity.current) { 12.dp.toSp() }
    Row(
        Modifier.size(106.dp, 40.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF00BD13)).clickable { onDial(index + 1) }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(if (index == 0) R.drawable.oppo_contacts_pb_ic_dial_call_sim1 else R.drawable.oppo_contacts_pb_ic_dial_call_sim2),
            contentDescription = "使用SIM${index + 1}拨打",
            modifier = Modifier.size(20.dp),
        )
        Text(label, Modifier.widthIn(max = 74.dp).padding(start = 4.dp),
            style = oppoTextStyle(textSize, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
