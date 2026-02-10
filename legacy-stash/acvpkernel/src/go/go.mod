module acvp

go 1.20

replace boringssl.googlesource.com/boringssl/util/fipstools/acvp/acvptool/acvp => ./acvp
replace boringssl.googlesource.com/boringssl/util/fipstools/acvp/acvptool/subprocess => ./subprocess

require(
    boringssl.googlesource.com/boringssl/util/fipstools/acvp/acvptool/acvp v0.0.0-00010101000000-000000000000
    boringssl.googlesource.com/boringssl/util/fipstools/acvp/acvptool/subprocess v0.0.0-00010101000000-000000000000
    golang.org/x/crypto v0.5.0
)

require (
    golang.org/x/sys v0.4.0 // indirect
    golang.org/x/term v0.4.0 // indirect
)
