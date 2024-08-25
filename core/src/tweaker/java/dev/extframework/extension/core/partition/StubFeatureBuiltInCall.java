package dev.extframework.extension.core.partition;

public class StubFeatureBuiltInCall {
    public static final String PARTITION_NAME_QUALIFIER = "<PARTITION>";
    public static final String FEATURE_SIGNATURE_QUALIFIER = "<FEATURE_SIG>";

    static Object __stub__() {
        final var partitionName = "<PARTITION>";
        final var signature = "<FEATURE_SIG>";

        final var builtIn = FeatureBuiltIn.class;

        final var codeSource = builtIn.getProtectionDomain().getCodeSource();
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
