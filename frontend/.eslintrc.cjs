module.exports = {
  root: true,
  env: { browser: true, es2020: true },
  extends: [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:react/recommended",
    "plugin:react-hooks/recommended",
    "prettier"
  ],
  ignorePatterns: ["dist", "node_modules", "@"],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaVersion: "latest",
    sourceType: "module"
  },
  plugins: ["@typescript-eslint", "react-refresh"],
  settings: {
    react: { version: "detect" }
  },
  rules: {
    "react/react-in-jsx-scope": "off",
    "react/prop-types": "off",
    "react-refresh/only-export-components": ["error", { allowConstantExport: true }]
  },
  overrides: [
    {
      files: ["src/router.tsx", "src/components/ui/**/*.tsx"],
      rules: {
        "react-refresh/only-export-components": "off"
      }
    },
    {
      files: ["src/stores/authStore.ts"],
      rules: {
        "@typescript-eslint/ban-ts-comment": "off"
      }
    }
  ]
};
