group = "app.morphe"

patches {
    about {
        name = "Kim Launcher Patches"
        description = "Morphe patches for Kim Launcher (com.kimkim) on BYD DiLink head units"
        source = "https://github.com/OWNER/kim-launcher-patches"
        author = "OWNER"
        contact = "https://github.com/OWNER/kim-launcher-patches/issues"
        website = "https://github.com/OWNER/kim-launcher-patches"
        license = "GNU General Public License v3.0"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}
