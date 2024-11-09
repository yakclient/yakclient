package dev.extframework.extension.core.partition;

import java.security.CodeSource;

public class StubFeatureBuiltInCall {
    public static final String PARTITION_NAME_QUALIFIER = "<PARTITION>";
    public static final String FEATURE_SIGNATURE_QUALIFIER = "<FEATURE_SIG>";

    static Object __stub__() {
        final String partitionName = "<PARTITION>";
        final String signature = "<FEATURE_SIG>";

        final Class<FeatureBuiltIn> builtIn = FeatureBuiltIn.class;

        final CodeSource codeSource = builtIn.getProtectionDomain().getCodeSource();
        if (codeSource == null) throw new RuntimeException(
                "Illegal environment! No code source found for: '" + builtIn + "'."
        );
        if (!(codeSource instanceof FeatureCodeSource)) throw new RuntimeException(
                "Illegal environment! Code source found for: '" + builtIn + "' but it is not of type 'FeatureCodeSource'"
        );

        return ((FeatureCodeSource) codeSource).getIntrinsics().__invoke__(
                builtIn.getClassLoader(),
                partitionName,
                signature
        );
    }
}
