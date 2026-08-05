/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    '.layout/*.liquid',
    '.sections/*.liquid',
    '.snippets/*.liquid',
    '.templates/*.liquid',
    '.templates/customers/*.liquid',

  ],
  theme: {
    container:{
      center:true,
      padding:'2rem '
    },
    extend: {},
  },
  plugins: [],
}

