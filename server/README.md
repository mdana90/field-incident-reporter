# Local API

Use Node.js 20 or later:

```sh
cd server
npm install
npm start
```

The server listens on port `3000` by default. Set `PORT` to change it. `npm test` runs temporary local API contract tests. See [API.md](./API.md) for the multipart fields and responses. The versioned `data/seed.json` starts empty; runtime incidents and uploads are ignored by Git.
