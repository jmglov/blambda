# Blambda!

Blambda! is a custom runtime for AWS Lambda that lets you write functions using
Babashka. It is based on the fantastic work that [Tatu
Tarvainen](https://github.com/tatut) did on taking care of the heavy lifting of
interacting with the Lambda runtime API to process function invocations in
[bb-lambda](https://github.com/tatut/bb-lambda). I'm using the
[`bootstrap.clj`](blob/main/bootstrap.clj) from that project directly, and have
just rewritten the machinery around it to remove Docker in favour of zip files,
which I think are simpler (but maybe not easier).

## Building

To build Blambda! with the default Babashka version and platform, run:

``` sh
bb build-runtime
```

To see what the default Babashka version and platform are, run:

``` sh
bb help
```

To build a custom runtime with Babashka 0.8.2 on linux-aarch64, run:

``` sh
bb build-runtime 0.8.2 linux-aarch64
```

To see what else you can do, run:

``` sh
bb tasks
```
