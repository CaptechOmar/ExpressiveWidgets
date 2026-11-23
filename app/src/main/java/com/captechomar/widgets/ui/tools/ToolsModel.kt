package com.captechomar.widgets.ui.tools

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.graphics.shapes.RoundedPolygon
import com.captechomar.widgets.R

/** One tool: a small page with its own content. */
class Tool(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    /** A tool that takes the whole screen, with its own floating controls (the ruler). */
    val fullScreen: Boolean = false,
    /** A tool that fills the space under its header instead of scrolling (the level). */
    val fill: Boolean = false,
    val content: @Composable () -> Unit,
)

/** A group of tools, shown as a coloured tile on the Tools tab. To add a category or a tool, add it to [ToolCategories]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ToolCategory(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    val shape: RoundedPolygon,
    val tools: List<Tool>,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object ToolCategories {
    val all: List<ToolCategory> = listOf(
        ToolCategory(
            "everyday", "Everyday", "Ruler, compass, level, barometer", R.drawable.cat_everyday, MaterialShapes.Arch,
            listOf(
                Tool("ruler", "Ruler", "Measure on your screen in cm or inches", R.drawable.tool_ruler, fullScreen = true) { RulerTool() },
                Tool("compass", "Compass", "Which way you are facing", R.drawable.tool_compass) { CompassTool() },
                Tool("level", "Level", "Check if a surface is flat", R.drawable.tool_level, fill = true) { LevelTool() },
                Tool("barometer", "Barometer", "Air pressure and altitude", R.drawable.tool_barometer) { BarometerTool() },
            ),
        ),
        ToolCategory(
            "health", "Health", "BMI, ideal weight, water, sleep", R.drawable.cat_health, MaterialShapes.Ghostish,
            listOf(
                Tool("bmi", "BMI calculator", "Body mass index from height and weight", R.drawable.tool_bmi) { BmiTool() },
                Tool("ideal", "Ideal weight", "A healthy range for your height", R.drawable.tool_ideal_weight) { IdealWeightTool() },
                Tool("water", "Water intake", "How much to drink in a day", R.drawable.tool_water) { WaterTool() },
                Tool("sleep", "Sleep calculator", "When to go to bed or wake up", R.drawable.tool_sleep) { SleepTool() },
            ),
        ),
        ToolCategory(
            "finance", "Finance", "Tips, loans, discounts", R.drawable.cat_finance, MaterialShapes.Fan,
            listOf(
                Tool("tip", "Tip and split", "Tip a bill and share it", R.drawable.tool_tip) { TipTool() },
                Tool("loan", "Loan payment", "Monthly payment and total interest", R.drawable.tool_loan) { LoanTool() },
                Tool("discount", "Discount", "The price after a sale", R.drawable.tool_discount) { DiscountTool() },
            ),
        ),
        ToolCategory(
            "random", "Randomizer", "Dice, coin, numbers, picker", R.drawable.cat_random, MaterialShapes.Cookie6Sided,
            listOf(
                Tool("dice", "Dice", "Roll one to four dice", R.drawable.tool_dice) { DiceTool() },
                Tool("coin", "Coin flip", "Heads or tails", R.drawable.tool_coin) { CoinTool() },
                Tool("number", "Random number", "Pick a number in a range", R.drawable.tool_number) { NumberTool() },
                Tool("picker", "Pick from a list", "Let the app choose for you", R.drawable.tool_picker) { PickerTool() },
            ),
        ),
    )
}
