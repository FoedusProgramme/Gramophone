/*
 *     Copyright (C) 2026 SteveZMTstudios
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.fragments.settings

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.app.DialogCompat
import androidx.core.widget.addTextChangedListener
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.BaseActivity
import uk.akane.libphonograph.utils.TagSplitter

/**
 * Secondary settings page to view and configure multi-artist and multi-genre split delimiters.
 *
 * @author SteveZMTstudios
 */
class SplitTagSettingsActivity : BaseActivity() {

    companion object {
        const val EXTRA_TYPE = "split_type"
        const val TYPE_ARTIST = "artist"
        const val TYPE_GENRE = "genre"

        fun createIntent(context: Context, type: String): Intent {
            return Intent(context, SplitTagSettingsActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type)
            }
        }
    }

    private lateinit var type: String
    private lateinit var chipGroupSymbols: ChipGroup
    private lateinit var chipGroupWords: ChipGroup
    private lateinit var layoutWordsSection: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_split_tag_settings)

        type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_ARTIST

        val appBarLayout = findViewById<AppBarLayout>(R.id.appbarlayout)
        val collapsingToolbar = findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val scrollView = findViewById<View>(R.id.scroll_view)

        appBarLayout.enableEdgeToEdgePaddingListener()
        scrollView.enableEdgeToEdgePaddingListener()

        val titleRes = if (type == TYPE_ARTIST) R.string.settings_split_artists else R.string.settings_split_genres
        collapsingToolbar.title = getString(titleRes)

        topAppBar.setNavigationOnClickListener {
            finish()
        }

        topAppBar.inflateMenu(R.menu.split_tag_settings_menu)
        topAppBar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_reset) {
                resetToDefaults()
                true
            } else {
                false
            }
        }

        chipGroupSymbols = findViewById(R.id.chip_group_symbols)
        chipGroupWords = findViewById(R.id.chip_group_words)
        layoutWordsSection = findViewById(R.id.layout_words_section)

        val btnAddSymbol = findViewById<Button>(R.id.btn_add_symbol)
        val btnAddWord = findViewById<Button>(R.id.btn_add_word)

        if (type == TYPE_GENRE) {
            layoutWordsSection.visibility = View.GONE
        }

        btnAddSymbol.setOnClickListener {
            showAddDialog(isWord = false)
        }

        btnAddWord.setOnClickListener {
            showAddDialog(isWord = true)
        }

        loadChips()
    }

    private fun getSymbols(): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return if (type == TYPE_ARTIST) TagSplitter.getArtistSymbols(prefs) else TagSplitter.getGenreSymbols(prefs)
    }

    private fun setSymbols(list: List<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (type == TYPE_ARTIST) TagSplitter.setArtistSymbols(prefs, list) else TagSplitter.setGenreSymbols(prefs, list)
    }

    private fun getWords(): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return TagSplitter.getArtistWords(prefs)
    }

    private fun setWords(list: List<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        TagSplitter.setArtistWords(prefs, list)
    }

    private fun loadChips() {
        chipGroupSymbols.removeAllViews()
        chipGroupWords.removeAllViews()

        for (symbol in getSymbols()) {
            chipGroupSymbols.addView(createChip(symbol) {
                val updated = getSymbols().toMutableList().apply { remove(symbol) }
                setSymbols(updated)
                loadChips()
            })
        }

        if (type == TYPE_ARTIST) {
            for (word in getWords()) {
                chipGroupWords.addView(createChip(word) {
                    val updated = getWords().toMutableList().apply { remove(word) }
                    setWords(updated)
                    loadChips()
                })
            }
        }
    }

    private fun createChip(text: String, onClose: () -> Unit): Chip {
        return Chip(this).apply {
            this.text = text
            isCloseIconVisible = true
            closeIconContentDescription = getString(R.string.split_remove_delimiter, text)
            setOnCloseIconClickListener {
                onClose()
            }
        }
    }

    private fun showAddDialog(isWord: Boolean) {
        val context = this
        val dialogTitle = if (isWord) R.string.split_add_word else R.string.split_add_symbol
        val d = MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle)
            .setView(R.layout.dialog_split_tag_input)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val et = DialogCompat.requireViewById(
                    dialog as AlertDialog,
                    R.id.editText
                ) as TextInputEditText
                val input = et.text?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    if (isWord) {
                        val words = getWords().toMutableList()
                        words.add(input)
                        setWords(words)
                    } else {
                        val symbols = getSymbols().toMutableList()
                        symbols.add(input)
                        setSymbols(symbols)
                    }
                    loadChips()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        val et = DialogCompat.requireViewById(d, R.id.editText) as TextInputEditText
        val inL = DialogCompat.requireViewById(d, R.id.inputLayout) as TextInputLayout
        val positiveBtn = d.getButton(DialogInterface.BUTTON_POSITIVE)
        positiveBtn.isEnabled = false

        inL.hint = getString(if (isWord) R.string.split_add_word_hint else R.string.split_add_symbol_hint)

        et.addTextChangedListener(afterTextChanged = {
            val input = it?.toString()?.trim() ?: ""
            if (input.isEmpty()) {
                inL.error = null
                positiveBtn.isEnabled = false
                return@addTextChangedListener
            }
            if (isWord) {
                if (input.contains('\\')) {
                    inL.error = getString(R.string.split_backslash_not_allowed)
                    positiveBtn.isEnabled = false
                    return@addTextChangedListener
                }
                if (it?.any { ch -> Character.isWhitespace(ch) } == true) {
                    inL.error = getString(R.string.split_word_no_whitespace)
                    positiveBtn.isEnabled = false
                    return@addTextChangedListener
                }
            } else {
                if (input.length > 1) {
                    inL.error = getString(R.string.split_symbol_single_char_only)
                    positiveBtn.isEnabled = false
                    return@addTextChangedListener
                }
                if (input == "\\") {
                    inL.error = getString(R.string.split_backslash_not_allowed)
                    positiveBtn.isEnabled = false
                    return@addTextChangedListener
                }
                if (Character.isLetterOrDigit(input[0])) {
                    inL.error = getString(R.string.split_symbol_no_letter_or_digit)
                    positiveBtn.isEnabled = false
                    return@addTextChangedListener
                }
            }
            val currentSymbols = getSymbols()
            val currentWords = if (type == TYPE_ARTIST) getWords() else emptyList()
            if (currentSymbols.contains(input) || currentWords.contains(input)) {
                inL.error = getString(R.string.split_delimiter_already_exists)
                positiveBtn.isEnabled = false
            } else {
                inL.error = null
                positiveBtn.isEnabled = true
            }
        })
    }

    private fun resetToDefaults() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (type == TYPE_ARTIST) {
            TagSplitter.setArtistSymbols(prefs, TagSplitter.DEFAULT_ARTIST_SYMBOLS)
            TagSplitter.setArtistWords(prefs, TagSplitter.DEFAULT_ARTIST_WORDS)
        } else {
            TagSplitter.setGenreSymbols(prefs, TagSplitter.DEFAULT_GENRE_SYMBOLS)
        }
        loadChips()
    }
}
