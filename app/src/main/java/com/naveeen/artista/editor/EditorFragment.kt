package com.naveeen.artista.editor

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.naveeen.artista.R
import com.naveeen.artista.custom.ListSpaceItemDecoration
import com.naveeen.artista.databinding.FragmentEditorBinding
import com.naveeen.artista.utils.ImageUtils
import com.naveeen.artista.utils.setSrcUri

class EditorFragment : Fragment() {

    private lateinit var editorViewModel: EditorViewModel
    private lateinit var adapter: StylesAdapter

    private lateinit var fadeInAnimator: ObjectAnimator
    private lateinit var fadeOutAnimator: ObjectAnimator

    private var isBusy: Boolean = false

    private var minDimensStyle: Int = 0

    companion object {
        private const val TAG = "EditorFragment"
    }

    private val photosResultLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                // Proceed only if minimum dimension is at least 256 pixels else show error toast
                if (validateDimensions(uri)) {
                    val style = Style("Custom", uri, Style.CUSTOM)
                    editorViewModel.addStyle(style)
                    applyStyle(style)
                } else Toast.makeText(
                    context,
                    resources.getString(R.string.error_small_dimension, minDimensStyle),
                    Toast.LENGTH_LONG
                ).show()
            } else Toast.makeText(
                context,
                resources.getString(R.string.error_unknown),
                Toast.LENGTH_SHORT
            ).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val binding: FragmentEditorBinding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_editor, container, false
        )

        requireNotNull(activity as AppCompatActivity).supportActionBar?.hide()
        minDimensStyle = resources.getInteger(R.integer.min_size_style)

        var uriString: String?

        arguments?.let { bundle ->
            val args = EditorFragmentArgs.fromBundle(bundle)
            uriString = args.mediaUri

            val application = requireNotNull(activity).application

            // Load view model
            val viewModelFactory = EditorViewModelFactory(Uri.parse(uriString), application)
            editorViewModel =
                ViewModelProvider(this, viewModelFactory).get(EditorViewModel::class.java)
            binding.editorViewModel = editorViewModel

            // Set up the list with adapter
            adapter = StylesAdapter(
                StylesAdapter.StyleClickListener(
                    { style -> applyStyle(style) },
                    { openPhotosActivity() })
            )
            val layoutManager = LinearLayoutManager(
                context,
                resources.getInteger(R.integer.orientation_styles_list), false
            )
            binding.stylesList.layoutManager = layoutManager
            val space = resources.getDimensionPixelSize(R.dimen.item_space_style)
            binding.stylesList.addItemDecoration(
                ListSpaceItemDecoration(
                    space
                )
            )
            binding.stylesList.adapter = adapter

            binding.lifecycleOwner = this

            // Load animators for showing/hiding progress views
            loadAnimators(binding.progressHolder)

            // Attach observers
            editorViewModel.processBusyLiveData.observe(viewLifecycleOwner, Observer {
                isBusy = it
                if (isBusy) fadeInAnimator.start()
                else fadeOutAnimator.start()
            })
            editorViewModel.stylesListLiveData.observe(viewLifecycleOwner, Observer { styles ->
                adapter.updateList(styles)
            })
            editorViewModel.currentStyleLiveData.observe(viewLifecycleOwner, Observer { style ->
                style?.let { adapter.showAsSelection(style) }
            })

            // Set initial preview as the original image itself. Set only if there is no styled
            // bitmap available, as if any styled bitmap is available it will be set automatically
            // using live data
            if (editorViewModel.styledBitmapLiveData.value == null)
                (binding.preview as ImageView).setSrcUri(Uri.parse(uriString))

            // Set listeners
            binding.controls.blendingSeekBar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) = editorViewModel.updateBlendRatio(progress.toFloat() / seekBar!!.max)

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}

            })
        }

        return binding.root
    }

    private fun applyStyle(style: Style) {
        if (isBusy) return

        editorViewModel.currentStyleLiveData.value = style
        editorViewModel.applyStyle(
            requireContext(),
            editorViewModel.originalMediaUri,
            style
        )
    }

    private fun openPhotosActivity() {
        if (isBusy) return
        photosResultLauncher.launch("image/*")
    }

    private fun validateDimensions(uri: Uri): Boolean {
        val size = ImageUtils.getImageSizeFromUri(requireContext(), uri)
        return size.width >= minDimensStyle && size.height >= minDimensStyle
    }

    private fun loadAnimators(targetView: View) {
        fadeInAnimator =
            (AnimatorInflater.loadAnimator(
                requireContext(),
                R.animator.animator_fade_in
            ) as ObjectAnimator)
                .apply {
                    target = targetView
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator?) {
                            targetView.visibility = View.VISIBLE
                        }
                    })
                }

        fadeOutAnimator =
            (AnimatorInflater.loadAnimator(
                requireContext(),
                R.animator.animator_fade_out
            ) as ObjectAnimator)
                .apply {
                    target = targetView
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            targetView.visibility = View.INVISIBLE
                        }
                    })
                }
    }
}