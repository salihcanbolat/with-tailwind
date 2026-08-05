document.addEventListener("DOMContentLoaded", function () {

    const menuBar = document.querySelector(".menuBar");
    const mobileMenu = document.querySelector(".mobilMenu");

    menuBar.addEventListener("click", function () {
        mobileMenu.classList.toggle("hidden");
    });

    const menuBar2 = document.querySelector(".menuBar2");
    const mobileMenu2 = document.querySelector(".mobileMenu2");

    menuBar2.addEventListener("click", function () {
        mobileMenu2.classList.toggle("hidden");
    });

    const menuBar3 = document.querySelector(".menuBar3");
    const mobileMenu3 = document.querySelector(".mobileMenu3");

    menuBar3.addEventListener("click", function () {
        mobileMenu3.classList.toggle("hidden");
    });

});