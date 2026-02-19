default:
    just --list

# start the quarkus dev server
dev:
    quarkus dev

# run tests once
ci-test:
    quarkus test --once
