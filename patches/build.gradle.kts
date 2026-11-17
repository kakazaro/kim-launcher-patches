group = "app.morphe"

patches {
    about {
        name = "Kim Launcher Patches"
        description = "Morphe patches for Kim Launcher (com.kimkim) on BYD DiLink head units"
        source = "https://github.com/kakazaro/kim-launcher-patches"
        author = "kakazaro"
        contact = "https://github.com/kakazaro/kim-launcher-patches/issues"
        website = "https://github.com/kakazaro/kim-launcher-patches"
        license = "GNU General Public License v3.0"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}
