package info.nightscout.androidaps.utils

import android.util.SparseArray
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.core.R

import info.nightscout.androidaps.utils.resources.ResourceHelper

class ActionModeHelper<T>(val rh: ResourceHelper, val activity: FragmentActivity?) {

    var enableSort = false
    private var selectedItems: SparseArray<T> = SparseArray()
    private var actionMode: ActionMode? = null
    private var removeActionMode: ActionMode? = null
    private var sortActionMode: ActionMode? = null
    private var onRemove: ((selectedItems: SparseArray<T>) -> Unit)? = null
    private var onUpdate: (() -> Unit)? = null

    val inMenu: Boolean
        get() {
            val parentClass = this.activity?.let { it::class.simpleName }
            return parentClass == "SingleFragmentActivity"
        }

    val enableRemove: Boolean
        get() = onRemove != null

    val isNoAction: Boolean
        get() = actionMode == null && removeActionMode == null && sortActionMode == null

    val isAction: Boolean
        get() = actionMode != null

    val isSorting: Boolean
        get() = sortActionMode != null

    val isRemoving: Boolean
        get() = removeActionMode != null

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_remove_items -> {
                removeActionMode = activity?.startActionMode(RemoveActionModeCallback())
                true
            }

            R.id.nav_sort_items   -> {
                sortActionMode = activity?.startActionMode(SortActionModeCallback())
                true
            }

            else                  -> false
        }
    }

    fun updateSelection(position: Int, item: T, selected: Boolean) {
        if (selected) {
            selectedItems.put(position, item)
        } else {
            selectedItems.remove(position)
        }
        removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
    }

    fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (inMenu) {
            inflater.inflate(R.menu.menu_actions, menu)
        }
    }

    fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.nav_remove_items)?.isVisible = enableRemove
        menu.findItem(R.id.nav_sort_items)?.isVisible = enableSort
    }

    fun startAction(): Boolean {
        if (isNoAction) {
            actionMode = activity?.startActionMode(ActionModeCallback())
            return true
        }
        return false
    }

    fun startRemove(): Boolean {
        if (removeActionMode == null) {
            removeActionMode = activity?.startActionMode(RemoveActionModeCallback())
            return true
        }
        return false
    }

    fun startSort(): Boolean {
        if (sortActionMode == null) {
            sortActionMode = activity?.startActionMode(SortActionModeCallback())
            return true
        }
        return false
    }

    fun isSelected(position: Int) =
        selectedItems.get(position) != null

    fun setOnRemoveHandler(onRemove: (selectedItems: SparseArray<T>) -> Unit) {
        this.onRemove = onRemove
    }

    fun setUpdateListHandler(onUpdate: () -> Unit) {
        this.onUpdate = onUpdate
    }

    fun finish() {
        actionMode?.finish()
        removeActionMode?.finish()
        sortActionMode?.finish()
    }

    private inner class ActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.title = activity?.title
            mode.menuInflater.inflate(R.menu.menu_actions, menu)
            onUpdate?.let { it() }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) =
            onOptionsItemSelected(item)

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
        }
    }

    private inner class SortActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.title = rh.gs(R.string.sort_label)
            onUpdate?.let { it() }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false

        override fun onDestroyActionMode(mode: ActionMode?) {
            sortActionMode = null
            onUpdate?.let { it() }
        }
    }

    private inner class RemoveActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.menuInflater.inflate(R.menu.menu_delete_selection, menu)
            selectedItems.clear()
            mode.title = rh.gs(R.string.count_selected, selectedItems.size())
            onUpdate?.let { it() }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.remove_selected -> {
                    if (selectedItems.size() > 0) {
                        onRemove?.let { it(selectedItems) }
                    } else {
                        finish()
                    }
                    true
                }

                else                 -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            removeActionMode = null
            onUpdate?.let { it() }
        }
    }

}
