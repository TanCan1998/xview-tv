package io.github.tancan1998.xviewtv.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp

/** Semantic tokens: palette and radii are resources, shared by every screen. */
object XViewColors {
    val background: Color @Composable get() = colorResource(R.color.xview_background)
    val surface: Color @Composable get() = colorResource(R.color.xview_surface)
    val primary: Color @Composable get() = colorResource(R.color.xview_primary)
    val muted: Color @Composable get() = colorResource(R.color.xview_muted)
    val text: Color @Composable get() = colorResource(R.color.xview_text)
    val focused: Color @Composable get() = colorResource(R.color.xview_surface_focused)
    val outline: Color @Composable get() = colorResource(R.color.xview_outline)
    val placeholder: Color @Composable get() = colorResource(R.color.xview_placeholder)
    val player: Color @Composable get() = colorResource(R.color.xview_player_background)
    val scrim: Color @Composable get() = colorResource(R.color.xview_scrim)
    val transparent: Color @Composable get() = colorResource(R.color.xview_transparent)
}
object XViewShapes {
    val small: RoundedCornerShape @Composable get() = RoundedCornerShape(dimensionResource(R.dimen.corner_small))
    val medium: RoundedCornerShape @Composable get() = RoundedCornerShape(dimensionResource(R.dimen.corner_medium))
    val card: RoundedCornerShape @Composable get() = RoundedCornerShape(dimensionResource(R.dimen.corner_card))
    val cardTop: RoundedCornerShape @Composable get() = RoundedCornerShape(topStart = dimensionResource(R.dimen.corner_card), topEnd = dimensionResource(R.dimen.corner_card))
    val section: RoundedCornerShape @Composable get() = RoundedCornerShape(dimensionResource(R.dimen.corner_section))
    val creator: RoundedCornerShape @Composable get() = RoundedCornerShape(dimensionResource(R.dimen.corner_creator))
}
