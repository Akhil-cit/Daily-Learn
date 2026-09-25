package com.example.daily_learn.ui.onboarding;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.daily_learn.R;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.databinding.FragmentSplashBinding;

public class SplashFragment extends Fragment {

    private FragmentSplashBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SettingsStore settings = new SettingsStore(requireContext());

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isAdded()) return;
            if (settings.isLoggedIn()) {
                Navigation.findNavController(view)
                    .navigate(R.id.action_splash_to_home);
            } else {
                Navigation.findNavController(view)
                    .navigate(R.id.action_splash_to_onboarding);
            }
        }, 1200);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
