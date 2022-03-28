package org.droidplanner.android.dialogs

import org.droidplanner.android.fragments.actionbar.SelectionListAdapter

class ReturnToHomeSelectionListDialog(private val viewAdapter: SelectionListAdapter<*>?) :
    SelectionListDialog() {
    init {
        viewAdapter?.setSelectionListener(this)
    }

    override fun getSelectionsAdapter(): SelectionListAdapter<*>? {
        return viewAdapter
    }

}