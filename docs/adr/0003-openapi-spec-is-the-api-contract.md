# The OpenAPI spec is the API contract

With a Go server and Go and TypeScript clients, a hand-maintained API would drift between them. The OpenAPI spec is written first and everything else is generated from it: Go server interfaces and the Go CLI's client with oapi-codegen, and the TypeScript types shared by the web, mobile and desktop apps with openapi-typescript. Changing the API means changing the spec, never a handler or client on its own.
