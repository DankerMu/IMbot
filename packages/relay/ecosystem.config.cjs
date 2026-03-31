module.exports = {
  apps: [
    {
      name: "imbot-relay",
      script: "dist/index.js",
      cwd: __dirname,
      exec_mode: "fork",
      instances: 1,
      autorestart: true,
      max_restarts: 10,
      restart_delay: 5000,
      out_file: "/opt/imbot/logs/relay.out.log",
      error_file: "/opt/imbot/logs/relay.error.log",
      merge_logs: true,
      time: true,
      // RELAY_* values are loaded from .env by the relay itself via dotenv.
      env: {
        NODE_ENV: "production"
      }
    }
  ]
};
