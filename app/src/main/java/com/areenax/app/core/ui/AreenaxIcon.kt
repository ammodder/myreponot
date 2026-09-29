package com.areenax.app.core.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.areenax.app.R

/*
 * Material-Symbol-name → bundled VectorDrawable resolution (SPEC/ICONS.md).
 * The web renders icons by NAME (Material Symbols font / data-driven icon
 * slots like Task.icon, Achievement.icon, notification type icons). Native
 * resolves the same names to the local drawables Task 1-b extracted.
 *
 * Filled variants exist for the icons the web renders with 'FILL' 1
 * (BottomNav tabs, pw toggles, check states, key/lock/verified/bolt/crown/
 * groups/person_add/account_balance/credit_card/account_balance_wallet/
 * emoji_events/home/group/person).
 */

/** Returns the drawable resource name for a Material Symbol name, or null. */
private fun drawableNameFor(symbol: String, filled: Boolean): String? {
    val base = when (symbol) {
        // aliases seen in the web code / data
        "trophy" -> "trophy"
        "warning" -> "error" // no ic_warning extracted; error glyph is the visual fallback
        "tournament" -> "tune"
        "qr_code_scanner" -> "qr_code_2"
        "quiz" -> "assignment"
        "chevron-right" -> "chevron_right"
        else -> symbol
    }.replace('-', '_')

    // ICON-04 fix: "groups" added — the web renders `groups` with FILL'1'
    // (JoinTeamSheet FILLED_ICON) and ic_groups_fill ships; previously the
    // filled variant was unreachable and the KDoc claimed otherwise.
    val reserved = setOf(
        "account_balance", "account_balance_wallet", "bolt", "check", "check_circle",
        "credit_card", "crown", "done", "done_all", "emoji_events", "group", "groups", "home",
        "key", "lock", "person", "person_add", "verified", "visibility", "visibility_off",
    )
    return if (filled && base in reserved) "ic_${base}_fill" else "ic_$base"
}

/**
 * Compile-time map of every bundled `ic_*` drawable. This replaces the former
 * `R.drawable::class.java.getField(...)` reflection lookup, which R8 broke in
 * release builds (A6-01/A7-01 BLOCKER: R$drawable fields were stripped as
 * unused after constant inlining, so every data-driven icon fell back to
 * ic_help in release only). Direct references are R8-safe and faster; the
 * fallback stays ic_help for unknown names.
 */
private val iconIds: Map<String, Int> = mapOf(
    "ic_account_balance" to R.drawable.ic_account_balance, "ic_account_balance_fill" to R.drawable.ic_account_balance_fill, "ic_account_balance_wallet" to R.drawable.ic_account_balance_wallet, "ic_account_balance_wallet_fill" to R.drawable.ic_account_balance_wallet_fill,
    "ic_account_circle" to R.drawable.ic_account_circle, "ic_add" to R.drawable.ic_add, "ic_add_card" to R.drawable.ic_add_card, "ic_add_circle" to R.drawable.ic_add_circle,
    "ic_add_photo_alternate" to R.drawable.ic_add_photo_alternate, "ic_admin_panel_settings" to R.drawable.ic_admin_panel_settings, "ic_arrow_back" to R.drawable.ic_arrow_back, "ic_arrow_downward" to R.drawable.ic_arrow_downward,
    "ic_arrow_drop_down" to R.drawable.ic_arrow_drop_down, "ic_arrow_forward" to R.drawable.ic_arrow_forward, "ic_arrow_upward" to R.drawable.ic_arrow_upward, "ic_assignment" to R.drawable.ic_assignment,
    "ic_backspace" to R.drawable.ic_backspace, "ic_balance" to R.drawable.ic_balance, "ic_bar_chart" to R.drawable.ic_bar_chart, "ic_bolt" to R.drawable.ic_bolt,
    "ic_bolt_fill" to R.drawable.ic_bolt_fill, "ic_broken_image" to R.drawable.ic_broken_image, "ic_calculate" to R.drawable.ic_calculate, "ic_calendar_month" to R.drawable.ic_calendar_month,
    "ic_call" to R.drawable.ic_call, "ic_campaign" to R.drawable.ic_campaign, "ic_cancel" to R.drawable.ic_cancel, "ic_card_giftcard" to R.drawable.ic_card_giftcard,
    "ic_chat_bubble" to R.drawable.ic_chat_bubble, "ic_check" to R.drawable.ic_check, "ic_check_circle" to R.drawable.ic_check_circle, "ic_check_circle_fill" to R.drawable.ic_check_circle_fill,
    "ic_check_fill" to R.drawable.ic_check_fill, "ic_checklist" to R.drawable.ic_checklist, "ic_chevron_left" to R.drawable.ic_chevron_left, "ic_chevron_right" to R.drawable.ic_chevron_right,
    "ic_close" to R.drawable.ic_close, "ic_cloud_upload" to R.drawable.ic_cloud_upload, "ic_content_copy" to R.drawable.ic_content_copy, "ic_credit_card" to R.drawable.ic_credit_card,
    "ic_credit_card_fill" to R.drawable.ic_credit_card_fill, "ic_crown" to R.drawable.ic_crown, "ic_crown_fill" to R.drawable.ic_crown_fill, "ic_dark_mode" to R.drawable.ic_dark_mode,
    "ic_delete" to R.drawable.ic_delete, "ic_description" to R.drawable.ic_description, "ic_diversity_3" to R.drawable.ic_diversity_3, "ic_done" to R.drawable.ic_done,
    "ic_done_all" to R.drawable.ic_done_all, "ic_done_all_fill" to R.drawable.ic_done_all_fill, "ic_done_fill" to R.drawable.ic_done_fill, "ic_download" to R.drawable.ic_download,
    "ic_edit" to R.drawable.ic_edit, "ic_edit_note" to R.drawable.ic_edit_note, "ic_emoji_events" to R.drawable.ic_emoji_events, "ic_emoji_events_fill" to R.drawable.ic_emoji_events_fill,
    "ic_error" to R.drawable.ic_error, "ic_expand_less" to R.drawable.ic_expand_less, "ic_expand_more" to R.drawable.ic_expand_more, "ic_forum" to R.drawable.ic_forum,
    "ic_gavel" to R.drawable.ic_gavel, "ic_grid_view" to R.drawable.ic_grid_view, "ic_group" to R.drawable.ic_group, "ic_group_add" to R.drawable.ic_group_add,
    "ic_group_fill" to R.drawable.ic_group_fill, "ic_groups" to R.drawable.ic_groups, "ic_groups_fill" to R.drawable.ic_groups_fill, "ic_help" to R.drawable.ic_help,
    "ic_home" to R.drawable.ic_home, "ic_home_fill" to R.drawable.ic_home_fill, "ic_hourglass_top" to R.drawable.ic_hourglass_top, "ic_how_to_reg" to R.drawable.ic_how_to_reg,
    "ic_image" to R.drawable.ic_image, "ic_info" to R.drawable.ic_info, "ic_inline_about_chevron_right" to R.drawable.ic_inline_about_chevron_right, "ic_inline_leaderboard_crown" to R.drawable.ic_inline_leaderboard_crown,
    "ic_inline_teamcreation_send" to R.drawable.ic_inline_teamcreation_send, "ic_inline_teamcreation_shield_check" to R.drawable.ic_inline_teamcreation_shield_check, "ic_inline_whatsappfab_whatsapp_logo" to R.drawable.ic_inline_whatsappfab_whatsapp_logo, "ic_key" to R.drawable.ic_key,
    "ic_key_fill" to R.drawable.ic_key_fill, "ic_keyboard" to R.drawable.ic_keyboard, "ic_keyboard_hide" to R.drawable.ic_keyboard_hide, "ic_leaderboard" to R.drawable.ic_leaderboard,
    "ic_lock" to R.drawable.ic_lock, "ic_lock_fill" to R.drawable.ic_lock_fill, "ic_lock_open" to R.drawable.ic_lock_open, "ic_logout" to R.drawable.ic_logout,
    "ic_lucide_arrow_left" to R.drawable.ic_lucide_arrow_left, "ic_lucide_arrow_right" to R.drawable.ic_lucide_arrow_right, "ic_lucide_check" to R.drawable.ic_lucide_check, "ic_lucide_chevron_down" to R.drawable.ic_lucide_chevron_down,
    "ic_lucide_chevron_left" to R.drawable.ic_lucide_chevron_left, "ic_lucide_chevron_right" to R.drawable.ic_lucide_chevron_right, "ic_lucide_chevron_up" to R.drawable.ic_lucide_chevron_up, "ic_lucide_circle" to R.drawable.ic_lucide_circle,
    "ic_lucide_grip_vertical" to R.drawable.ic_lucide_grip_vertical, "ic_lucide_minus" to R.drawable.ic_lucide_minus, "ic_lucide_more_horizontal" to R.drawable.ic_lucide_more_horizontal, "ic_lucide_panel_left" to R.drawable.ic_lucide_panel_left,
    "ic_lucide_search" to R.drawable.ic_lucide_search, "ic_lucide_x" to R.drawable.ic_lucide_x, "ic_mail" to R.drawable.ic_mail, "ic_map" to R.drawable.ic_map,
    "ic_military_tech" to R.drawable.ic_military_tech, "ic_my_location" to R.drawable.ic_my_location, "ic_no_photography" to R.drawable.ic_no_photography, "ic_notifications" to R.drawable.ic_notifications,
    "ic_outbox" to R.drawable.ic_outbox, "ic_payment_arrow_down" to R.drawable.ic_payment_arrow_down, "ic_payments" to R.drawable.ic_payments, "ic_person" to R.drawable.ic_person,
    "ic_person_add" to R.drawable.ic_person_add, "ic_person_add_fill" to R.drawable.ic_person_add_fill, "ic_person_fill" to R.drawable.ic_person_fill, "ic_person_search" to R.drawable.ic_person_search,
    "ic_publish" to R.drawable.ic_publish,
    // ICON-02: design-page glyphs staged + verified in Phase 2, now bundled
    "ic_qr_code" to R.drawable.ic_qr_code,
    "ic_qr_code_2" to R.drawable.ic_qr_code_2,
    "ic_sports_handball" to R.drawable.ic_sports_handball,
    "ic_unfold_more" to R.drawable.ic_unfold_more,
    "ic_radio_button_unchecked" to R.drawable.ic_radio_button_unchecked, "ic_receipt_long" to R.drawable.ic_receipt_long,
    "ic_refresh" to R.drawable.ic_refresh, "ic_remove_circle_outline" to R.drawable.ic_remove_circle_outline, "ic_save" to R.drawable.ic_save, "ic_schedule" to R.drawable.ic_schedule,
    "ic_screenshot_monitor" to R.drawable.ic_screenshot_monitor, "ic_search" to R.drawable.ic_search, "ic_send" to R.drawable.ic_send, "ic_sentiment_satisfied" to R.drawable.ic_sentiment_satisfied,
    "ic_share" to R.drawable.ic_share, "ic_shield" to R.drawable.ic_shield, "ic_sports_esports" to R.drawable.ic_sports_esports, "ic_stadium" to R.drawable.ic_stadium,
    "ic_star" to R.drawable.ic_star, "ic_support_agent" to R.drawable.ic_support_agent, "ic_swap_horiz" to R.drawable.ic_swap_horiz, "ic_sync_alt" to R.drawable.ic_sync_alt,
    "ic_tag" to R.drawable.ic_tag, "ic_task_alt" to R.drawable.ic_task_alt, "ic_timer" to R.drawable.ic_timer, "ic_timer_off" to R.drawable.ic_timer_off,
    "ic_toggle_off" to R.drawable.ic_toggle_off, "ic_toggle_on" to R.drawable.ic_toggle_on, "ic_touch_app" to R.drawable.ic_touch_app, "ic_trophy" to R.drawable.ic_trophy,
    "ic_tune" to R.drawable.ic_tune, "ic_upload" to R.drawable.ic_upload, "ic_verified" to R.drawable.ic_verified, "ic_verified_fill" to R.drawable.ic_verified_fill,
    "ic_visibility" to R.drawable.ic_visibility, "ic_visibility_fill" to R.drawable.ic_visibility_fill, "ic_visibility_off" to R.drawable.ic_visibility_off, "ic_visibility_off_fill" to R.drawable.ic_visibility_off_fill,
    "ic_wallet" to R.drawable.ic_wallet, "ic_wifi_off" to R.drawable.ic_wifi_off,
)

/** Resolves a Material Symbol name to a drawable resource ID (fallback: help). */
fun iconResId(name: String, filled: Boolean = false): Int {
    val name2 = drawableNameFor(name, filled) ?: return R.drawable.ic_help
    return iconIds[name2] ?: R.drawable.ic_help
}

/**
 * The universal icon composable: renders any Material Symbol name from the
 * bundled drawables. `filled=true` uses the FILL'1' variant when available.
 */
@Composable
fun AreenaxIcon(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(iconResId(name, filled)),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
