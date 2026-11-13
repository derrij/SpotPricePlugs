---
layout: default
lang: fi
home: /fi/
blog: /fi/blog/
title: Blogi
description: "Käytännön oppaat Nord Pool -hinnoista, älypistorasioista ja oikeista säästöistä."
permalink: /fi/blog/
---

# Blogi

Käytännön oppaat Nord Pool -hinnoista, älypistorasioista ja oikeista säästöistä.

{% assign loc = site.posts | where: "lang", "fi" %}
{% for post in loc %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }}</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% if page.lang != 'en' %}
<h2>Lisää oppaita (englanniksi)</h2>
{% assign enposts = site.posts | where: "lang", "en" %}
{% for post in enposts %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }} · EN</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% endif %}
