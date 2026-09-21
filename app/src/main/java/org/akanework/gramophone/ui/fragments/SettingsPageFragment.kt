package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.ui.MainActivity

class SettingsPageFragment : Fragment() {

    companion object {
        const val ARG_TITLE = "title"
        const val ARG_FRAGMENT = "fragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_top_settings, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val collapsingToolbar = rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        rootView.findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()
        collapsingToolbar.title = getString(requireArguments().getInt(ARG_TITLE))
        topAppBar.setNavigationOnClickListener {
            (requireActivity() as MainActivity).navigateUp()
        }
        if (savedInstanceState == null) {
            val fragment = childFragmentManager.fragmentFactory.instantiate(
                requireContext().classLoader,
                requireArguments().getString(ARG_FRAGMENT)!!
            )
            childFragmentManager.beginTransaction().add(R.id.settings, fragment).commit()
        }
        return rootView
    }
}
